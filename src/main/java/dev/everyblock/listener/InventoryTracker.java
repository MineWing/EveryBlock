package dev.everyblock.listener;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.gui.BlockRarity;
import dev.everyblock.model.Contributor;
import dev.everyblock.model.Discovery;
import dev.everyblock.util.Text;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.IllegalPluginAccessException;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class InventoryTracker implements Listener {
    private static final long COMPLETION_DELAY_TICKS = 20;
    private static final long NANOS_PER_TICK = 50_000_000L;

    private final EveryBlockPlugin plugin;
    // Blocks an admin removed while these players held them. Session-only; server thread only.
    private final Map<UUID, Set<Material>> suppressed = new HashMap<>();
    private BukkitTask task;
    private boolean running;

    public InventoryTracker(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        running = true;
        long interval = plugin.settings().scanIntervalTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () ->
                plugin.getServer().getOnlinePlayers().forEach(this::scan), interval, interval);
        plugin.getServer().getOnlinePlayers().forEach(this::scanSoon);
    }

    public void stop() {
        running = false;
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isParticipant(Player player) {
        PluginSettings settings = plugin.settings();
        return settings.participantMode() == PluginSettings.ParticipantMode.EVERYONE
                || settings.allowlist().contains(player.getUniqueId());
    }

    public void scan(Player player) {
        if (!player.isOnline() || !isParticipant(player) || ignoredGameMode(player)) {
            return;
        }
        boolean trackBlocks = player.hasPermission("everyblock.use");
        boolean trackItems = plugin.settings().itemsEnabled() && player.hasPermission("everyblock.items");
        if (!trackBlocks && !trackItems) {
            return;
        }
        Set<Material> ignored = suppressed.get(player.getUniqueId());
        Set<Material> held = ignored == null ? null : EnumSet.noneOf(Material.class);
        Set<Material> candidates = new LinkedHashSet<>();
        Set<Material> itemCandidates = new LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack == null) {
                continue;
            }
            Material type = stack.getType();
            if (held != null) {
                held.add(type);
            }
            if (trackBlocks && plugin.catalogue().contains(type) && !plugin.progress().found(type)
                    && (ignored == null || !ignored.contains(type))) {
                candidates.add(type);
            }
            if (trackItems && plugin.itemCatalogue().contains(type) && !plugin.itemProgress().found(type)) {
                itemCandidates.add(type);
            }
        }
        if (ignored != null) {
            // Once the player no longer holds a removed block, picking it up again counts normally.
            ignored.retainAll(held);
            if (ignored.isEmpty()) {
                suppressed.remove(player.getUniqueId());
            }
        }
        if (!candidates.isEmpty()) {
            record(player, candidates);
        }
        if (!itemCandidates.isEmpty()) {
            recordItems(player, itemCandidates);
        }
    }

    /**
     * Stops online players who currently hold {@code material} from re-discovering it until a scan
     * sees they no longer hold it. Called after an admin removes the block from the collected list.
     */
    public void suppressHolders(Material material) {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (holds(player, material)) {
                suppressed.computeIfAbsent(player.getUniqueId(), id -> EnumSet.noneOf(Material.class)).add(material);
            }
        }
    }

    public void clearSuppressions() {
        suppressed.clear();
    }

    boolean suppressed(UUID playerId, Material material) {
        Set<Material> materials = suppressed.get(playerId);
        return materials != null && materials.contains(material);
    }

    private static boolean holds(Player player, Material material) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.getType() == material) {
                return true;
            }
        }
        return false;
    }

    private void record(Player player, Set<Material> candidates) {
        plugin.progress().discoverMany(candidates, player.getUniqueId(), player.getName()).whenComplete((saved, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("Could not save block discovery: " + ProgressDatabase.describe(failure));
                if (player.isOnline()) plugin.messages().send(player, "database-error");
                return;
            }
            announce(player, new ArrayList<>(saved));
        });
    }

    private void announce(Player player, List<Material> found) {
        found.removeIf(material -> !plugin.catalogue().contains(material));
        int before = plugin.progress().collectedCount() - found.size();
        if (found.isEmpty()) {
            return;
        }

        found.sort(Comparator.comparing(material -> Text.pretty(material.name())));
        int after = plugin.progress().collectedCount();
        int total = plugin.progress().totalCount();
        Map<String, String> common = replacements(player.getName(), after, total);
        if (found.size() == 1) {
            Material material = found.getFirst();
            common = with(common, "block", display(material));
            String message = switch (BlockRarity.of(material)) {
                case EPIC -> "block-found-epic";
                case LEGENDARY -> "block-found-legendary";
                default -> "block-found";
            };
            plugin.messages().sendAll(message, common);
        } else {
            common = with(common, "amount", Integer.toString(found.size()));
            plugin.messages().sendAll("blocks-found", common);
            for (Material material : found) {
                plugin.messages().sendAll("blocks-found-row", with(common, "block", display(material)));
            }
        }
        celebrateRarities(found, common);
        progressChanged(before, after, total, player.getName(), found.getLast());
        plugin.gui().refreshOpenMenus();
    }

    /**
     * Announces any milestone crossed between {@code before} and {@code after} collected blocks and
     * starts the finale when this change completed the challenge. Every path that adds block
     * progress (scans and admin additions) goes through here, so each transition fires once.
     */
    public void progressChanged(int before, int after, int total, String playerName, Material lastBlock) {
        if (after <= before) {
            return;
        }
        Map<String, String> common = replacements(playerName, after, total);
        int beforePercent = total == 0 ? 100 : before * 100 / total;
        int afterPercent = total == 0 ? 100 : after * 100 / total;
        int crossed = plugin.settings().milestones().stream()
                .filter(milestone -> milestone > beforePercent && milestone <= afterPercent)
                .max(Integer::compareTo).orElse(-1);
        if (crossed > 0) {
            plugin.messages().sendAll("milestone", with(common,
                    "percent", Integer.toString(crossed),
                    "remaining", Integer.toString(Math.max(0, total - after))));
        }
        if (before < total && after >= total) {
            completionSequence(with(common, "block", display(lastBlock)));
        }
    }

    /**
     * Called after a reload rebuilt the catalogues. A smaller catalogue can complete a challenge
     * without any new discovery; the latest discovery is then credited as the final one.
     */
    public void catalogueChanged(boolean blocksWereComplete, boolean itemsWereComplete) {
        int collected = plugin.progress().collectedCount();
        int total = plugin.progress().totalCount();
        if (!blocksWereComplete && complete(collected, total)) {
            plugin.progress().latest().ifPresent(last -> completionSequence(with(
                    replacements(last.playerName(), collected, total), "block", display(last.material()))));
        }
        if (!plugin.settings().itemsEnabled() || itemsWereComplete) {
            return;
        }
        int items = plugin.itemProgress().collectedCount();
        int itemTotal = plugin.itemProgress().totalCount();
        if (complete(items, itemTotal)) {
            List<Discovery> recent = plugin.itemProgress().recent();
            if (!recent.isEmpty()) {
                Discovery last = recent.getFirst();
                plugin.messages().sendAll("items-complete", with(replacements(last.playerName(), items, itemTotal),
                        "item", display(last.material())));
            }
        }
    }

    public static boolean complete(int collected, int total) {
        return total > 0 && collected >= total;
    }

    private void recordItems(Player player, Set<Material> candidates) {
        plugin.itemProgress().discoverMany(candidates, player.getUniqueId(), player.getName()).whenComplete((saved, failure) -> {
            if (failure != null) {
                plugin.getLogger().severe("Could not save item discovery: " + ProgressDatabase.describe(failure));
                if (player.isOnline()) plugin.messages().send(player, "database-error");
                return;
            }
            announceItems(player, new ArrayList<>(saved));
        });
    }

    private void announceItems(Player player, List<Material> found) {
        if (!plugin.settings().itemsEnabled()) {
            return;
        }
        found.removeIf(material -> !plugin.itemCatalogue().contains(material));
        int before = plugin.itemProgress().collectedCount() - found.size();
        if (found.isEmpty()) {
            return;
        }
        found.sort(Comparator.comparing(material -> Text.pretty(material.name())));
        int after = plugin.itemProgress().collectedCount();
        int total = plugin.itemProgress().totalCount();
        Map<String, String> common = replacements(player.getName(), after, total);
        if (found.size() == 1) {
            plugin.messages().sendAll("item-found", with(common, "item", display(found.getFirst())));
        } else {
            common = with(common, "amount", Integer.toString(found.size()));
            plugin.messages().sendAll("items-found", common);
            for (Material material : found) {
                plugin.messages().sendAll("items-found-row", with(common, "item", display(material)));
            }
        }
        if (before < total && after >= total) {
            plugin.messages().sendAll("items-complete", with(common, "item", display(found.getLast())));
        }
        plugin.itemGui().refreshOpenMenus();
    }

    private void celebrateRarities(List<Material> found, Map<String, String> common) {
        List<Material> featured = featuredRarities(found);
        boolean batch = found.size() > 1;
        for (int index = 0; index < featured.size(); index++) {
            Material material = featured.get(index);
            if (index == 0) {
                sendRarityCelebration(material, common, batch);
            } else {
                later(index * 65L, () -> sendRarityCelebration(material, common, batch));
            }
        }
    }

    static List<Material> featuredRarities(List<Material> found) {
        if (found.size() == 1) {
            return List.copyOf(found);
        }
        Map<BlockRarity, Material> highRarities = new LinkedHashMap<>();
        found.stream()
                .sorted(Comparator.comparing(BlockRarity::of))
                .filter(material -> BlockRarity.of(material).compareTo(BlockRarity.RARE) >= 0)
                .forEach(material -> highRarities.putIfAbsent(BlockRarity.of(material), material));
        if (!highRarities.isEmpty()) {
            return List.copyOf(highRarities.values());
        }
        return found.stream()
                .max(Comparator.comparing(BlockRarity::of))
                .map(List::of)
                .orElseGet(List::of);
    }

    private void sendRarityCelebration(Material featured, Map<String, String> common, boolean batch) {
        BlockRarity rarity = BlockRarity.of(featured);
        Map<String, String> replacements = with(common,
                "block", display(featured),
                "rarity", rarity.miniMessage(),
                "rarity_name", Text.escape(rarity.displayName()));
        if (batch && (rarity == BlockRarity.EPIC || rarity == BlockRarity.LEGENDARY)) {
            plugin.messages().sendAll("rarity-" + rarity.name().toLowerCase(Locale.ROOT) + "-message",
                    replacements);
        }
        plugin.messages().sendAll("rarity-" + rarity.name().toLowerCase(Locale.ROOT), replacements);
    }

    private void completionSequence(Map<String, String> common) {
        List<Contributor> contributors = plugin.progress().contributors();
        long onlineTicks = 0;
        List<UUID> offline = new ArrayList<>();
        for (Contributor contributor : contributors) {
            if (contributor.playerId().equals(EveryBlockPlugin.CONSOLE_UUID)) {
                continue;
            }
            Player online = plugin.getServer().getPlayer(contributor.playerId());
            if (online != null) {
                onlineTicks += Math.max(0, online.getStatistic(Statistic.PLAY_ONE_MINUTE));
            } else {
                offline.add(contributor.playerId());
            }
        }
        if (offline.isEmpty()) {
            playCompletion(common, contributors, onlineTicks, COMPLETION_DELAY_TICKS);
            return;
        }
        // Offline statistics are read from each player's stats file, so keep that off the server thread.
        long liveTicks = onlineTicks;
        long started = System.nanoTime();
        try {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                long storedTicks = offlinePlaytime(offline);
                if (!plugin.isEnabled()) {
                    return;
                }
                try {
                    plugin.getServer().getScheduler().runTask(plugin, () -> {
                        if (!plugin.isEnabled()) {
                            return;
                        }
                        long elapsed = (System.nanoTime() - started) / NANOS_PER_TICK;
                        playCompletion(common, contributors, liveTicks + storedTicks,
                                Math.max(1, COMPLETION_DELAY_TICKS - elapsed));
                    });
                } catch (IllegalPluginAccessException disabled) {
                    // The plugin was disabled while statistics were loading.
                }
            });
        } catch (IllegalPluginAccessException disabled) {
            // The plugin is shutting down; skip the finale.
        }
    }

    private long offlinePlaytime(List<UUID> players) {
        long total = 0;
        for (UUID playerId : players) {
            try {
                total += Math.max(0, plugin.getServer().getOfflinePlayer(playerId)
                        .getStatistic(Statistic.PLAY_ONE_MINUTE));
            } catch (RuntimeException failure) {
                plugin.getLogger().fine("Could not read playtime for " + playerId + ": " + failure.getMessage());
            }
        }
        return total;
    }

    private void playCompletion(Map<String, String> common, List<Contributor> contributors,
                                long playtimeTicks, long delay) {
        Map<String, String> summary = with(common,
                "playtime", Text.durationTicks(playtimeTicks),
                "contributors", Integer.toString(contributors.size()));

        later(delay, () -> plugin.messages().sendAll("complete-start", summary));
        later(delay + 50, () -> plugin.messages().sendAll("complete-final-block", summary));
        later(delay + 100, () -> {
            plugin.messages().sendAll("complete-stats", summary);
            for (int index = 0; index < contributors.size(); index++) {
                Contributor contributor = contributors.get(index);
                plugin.messages().sendAll("complete-contributor", with(summary,
                        "position", Integer.toString(index + 1),
                        "contributor", Text.escape(contributor.playerName()),
                        "amount", Integer.toString(contributor.amount())));
            }
        });
        later(delay + 170, () -> plugin.messages().sendAll("complete-finale", summary));
    }

    private void later(long ticks, Runnable action) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isEnabled()) {
                action.run();
            }
        }, ticks);
    }

    private Map<String, String> replacements(String playerName, int collected, int total) {
        return Map.of(
                "player", Text.escape(playerName),
                "collected", Integer.toString(collected),
                "total", Integer.toString(total),
                "remaining", Integer.toString(Math.max(0, total - collected)),
                "percent", Text.percent(collected, total)
        );
    }

    private static Map<String, String> with(Map<String, String> base, String... entries) {
        HashMap<String, String> result = new HashMap<>(base);
        for (int index = 0; index + 1 < entries.length; index += 2) {
            result.put(entries[index], entries[index + 1]);
        }
        return Map.copyOf(result);
    }

    public static String display(Material material) {
        String name = Text.escape(Text.pretty(material.name()));
        BlockRarity rarity = BlockRarity.of(material);
        if (rarity == BlockRarity.EPIC || rarity == BlockRarity.LEGENDARY) {
            return "<bold>" + rarity.style(name) + "</bold>";
        }
        String accent = Text.accentColor();
        String close = accent.startsWith("gradient") ? "gradient" : accent;
        return "<" + accent + "><bold>" + name + "</bold></" + close + ">";
    }

    private boolean ignoredGameMode(Player player) {
        return !plugin.settings().countCreativeMode()
                && (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR);
    }

    private void scanSoon(Player player) {
        if (!running || !plugin.isEnabled()) {
            return;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (running && plugin.isEnabled()) {
                scan(player);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        scanSoon(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            scanSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            scanSoon(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onInventoryClose(InventoryCloseEvent event) {
        if (event.getPlayer() instanceof Player player) {
            scanSoon(player);
        }
    }
}
