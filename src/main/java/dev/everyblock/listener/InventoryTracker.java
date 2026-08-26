package dev.everyblock.listener;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.gui.BlockRarity;
import dev.everyblock.model.Contributor;
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
import org.bukkit.scheduler.BukkitTask;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class InventoryTracker implements Listener {
    private final EveryBlockPlugin plugin;
    private BukkitTask task;

    public InventoryTracker(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        long interval = plugin.settings().scanIntervalTicks();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () ->
                plugin.getServer().getOnlinePlayers().forEach(this::scan), interval, interval);
        plugin.getServer().getOnlinePlayers().forEach(this::scanSoon);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    public boolean isParticipant(Player player) {
        if (!player.hasPermission("everyblock.use")) {
            return false;
        }
        PluginSettings settings = plugin.settings();
        return settings.participantMode() == PluginSettings.ParticipantMode.EVERYONE
                || settings.allowlist().contains(player.getUniqueId());
    }

    public void scan(Player player) {
        if (!player.isOnline() || !isParticipant(player) || ignoredGameMode(player)) {
            return;
        }
        Set<Material> candidates = new LinkedHashSet<>();
        Set<Material> itemCandidates = new LinkedHashSet<>();
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && !stack.getType().isAir()) {
                if (plugin.catalogue().contains(stack.getType()) && !plugin.progress().found(stack.getType())) {
                    candidates.add(stack.getType());
                }
                if (plugin.settings().itemsEnabled() && plugin.itemCatalogue().contains(stack.getType())
                        && !plugin.itemProgress().found(stack.getType())) {
                    itemCandidates.add(stack.getType());
                }
            }
        }
        if (!candidates.isEmpty()) {
            record(player, candidates);
        }
        if (!itemCandidates.isEmpty()) {
            recordItems(player, itemCandidates);
        }
    }

    private void record(Player player, Set<Material> candidates) {
        int before = plugin.progress().collectedCount();
        List<Material> found = new ArrayList<>();
        try {
            for (Material material : candidates) {
                if (plugin.progress().discover(material, player.getUniqueId(), player.getName())) {
                    found.add(material);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not save block discovery: " + exception.getMessage());
            plugin.messages().send(player, "database-error");
            return;
        }
        if (found.isEmpty()) {
            return;
        }

        found.sort(Comparator.comparing(material -> Text.pretty(material.name())));
        int after = plugin.progress().collectedCount();
        int total = plugin.progress().totalCount();
        Map<String, String> common = replacements(player, after, total);
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
            completionSequence(with(common,
                    "block", display(found.getLast()),
                    "player", Text.escape(player.getName())));
        }
        plugin.gui().refreshOpenMenus();
    }

    private void recordItems(Player player, Set<Material> candidates) {
        int before = plugin.itemProgress().collectedCount();
        List<Material> found = new ArrayList<>();
        try {
            for (Material material : candidates) {
                if (plugin.itemProgress().discover(material, player.getUniqueId(), player.getName())) {
                    found.add(material);
                }
            }
        } catch (SQLException exception) {
            plugin.getLogger().severe("Could not save item discovery: " + exception.getMessage());
            plugin.messages().send(player, "database-error");
            return;
        }
        if (found.isEmpty()) {
            return;
        }
        found.sort(Comparator.comparing(material -> Text.pretty(material.name())));
        int after = plugin.itemProgress().collectedCount();
        int total = plugin.itemProgress().totalCount();
        Map<String, String> common = replacements(player, after, total);
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
            plugin.messages().sendAll("items-complete", with(common,
                    "item", display(found.getLast()), "player", Text.escape(player.getName())));
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
        long playtimeTicks = contributors.stream()
                .filter(contributor -> !contributor.playerId().equals(EveryBlockPlugin.CONSOLE_UUID))
                .mapToLong(contributor -> Math.max(0, plugin.getServer().getOfflinePlayer(contributor.playerId())
                        .getStatistic(Statistic.PLAY_ONE_MINUTE)))
                .sum();
        Map<String, String> summary = with(common,
                "playtime", Text.durationTicks(playtimeTicks),
                "contributors", Integer.toString(contributors.size()));

        later(20, () -> plugin.messages().sendAll("complete-start", summary));
        later(70, () -> plugin.messages().sendAll("complete-final-block", summary));
        later(120, () -> {
            plugin.messages().sendAll("complete-stats", summary);
            for (int index = 0; index < contributors.size(); index++) {
                Contributor contributor = contributors.get(index);
                plugin.messages().sendAll("complete-contributor", with(summary,
                        "position", Integer.toString(index + 1),
                        "contributor", Text.escape(contributor.playerName()),
                        "amount", Integer.toString(contributor.amount())));
            }
        });
        later(190, () -> plugin.messages().sendAll("complete-finale", summary));
    }

    private void later(long ticks, Runnable action) {
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (plugin.isEnabled()) {
                action.run();
            }
        }, ticks);
    }

    private Map<String, String> replacements(Player player, int collected, int total) {
        return Map.of(
                "player", Text.escape(player.getName()),
                "collected", Integer.toString(collected),
                "total", Integer.toString(total),
                "remaining", Integer.toString(Math.max(0, total - collected)),
                "percent", Text.percent(collected, total)
        );
    }

    private static Map<String, String> with(Map<String, String> base, String... entries) {
        java.util.HashMap<String, String> result = new java.util.HashMap<>(base);
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
        plugin.getServer().getScheduler().runTask(plugin, () -> scan(player));
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
