package dev.everyblock.message;

import dev.everyblock.util.Text;
import dev.everyblock.config.StrictYaml;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityRemoveEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

public final class MessageService implements Listener {
    private static final NamespacedKey CELEBRATION = new NamespacedKey("everyblock", "celebration");
    /** Recipients within this many blocks of an existing launch point share its firework. */
    static final double FIREWORK_CLUSTER_RADIUS = 16.0;
    /** Upper bound on fireworks spawned per launch round, regardless of player count. */
    static final int MAX_FIREWORK_LAUNCH_POINTS = 32;
    private final Map<BossBar, List<Player>> activeBossBars = new HashMap<>();
    private final Set<Firework> activeFireworks = new HashSet<>();
    private final ResolutionCache<Sound> sounds = new ResolutionCache<>(MessageService::lookupSound);
    private final ResolutionCache<Particle> particles = new ResolutionCache<>(MessageService::lookupParticle);
    private final JavaPlugin plugin;
    private YamlConfiguration messages;

    public MessageService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        apply(prepare());
    }

    public YamlConfiguration prepare() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.isFile()) {
            plugin.saveResource("messages.yml", false);
        }
        return StrictYaml.load(file);
    }

    public void apply(YamlConfiguration prepared) {
        messages = Objects.requireNonNull(prepared);
        sounds.clear();
        particles.clear();
    }

    public void send(CommandSender recipient, String key) {
        send(recipient, key, Map.of());
    }

    public void send(CommandSender recipient, String key, Map<String, String> replacements) {
        execute(key, List.of(recipient), replacements);
    }

    public void sendAll(String key, Map<String, String> replacements) {
        execute(key, new ArrayList<>(plugin.getServer().getOnlinePlayers()), replacements);
    }

    private void execute(String key, Collection<? extends CommandSender> recipients,
                         Map<String, String> replacements) {
        ConfigurationSection section = messages.getConfigurationSection(key);
        if (section == null || !section.getBoolean("enabled", true)) {
            return;
        }
        Map<String, String> values = withDefaults(replacements);
        for (String configured : section.getStringList("actions")) {
            ParsedAction action = parse(replace(configured, values));
            if (action == null) {
                warn(key, configured);
                continue;
            }
            try {
                dispatch(action.tag(), action.payload(), recipients);
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Invalid [" + action.tag() + "] action in '" + key
                        + "': " + exception.getMessage());
            }
        }
    }

    /**
     * Raw MiniMessage text for GUI titles, item names and lore lines - the same
     * placeholder pipeline as {@link #send}, but returning a Component instead of
     * dispatching an action list.
     */
    public Component text(String key, Map<String, String> replacements) {
        String raw = string(key, replacements, null);
        if (raw == null) {
            plugin.getLogger().warning("Missing message key: " + key);
            return Component.text(key);
        }
        return Text.parse(raw);
    }

    public Component text(String key) {
        return text(key, Map.of());
    }

    public Component textOrNull(String key, Map<String, String> replacements) {
        String raw = string(key, replacements, null);
        return raw == null ? null : Text.parse(raw);
    }

    /**
     * Configurable MiniMessage lore. YAML lists are preferred, while a scalar
     * string remains valid so existing messages.yml files keep working.
     */
    public List<Component> lines(String key, Map<String, String> replacements) {
        Map<String, String> values = withDefaults(replacements);
        return configuredLines(messages.get(key)).stream()
                .map(line -> Text.parse(replace(line, values)))
                .toList();
    }

    public List<Component> lines(String key) {
        return lines(key, Map.of());
    }

    static List<String> configuredLines(Object configured) {
        if (configured instanceof String line) {
            return line.isEmpty() ? List.of() : List.of(line);
        }
        if (!(configured instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    public String string(String key, Map<String, String> replacements, String fallback) {
        String raw = messages.getString(key);
        return raw == null ? fallback : replace(raw, withDefaults(replacements));
    }

    /**
     * Applies %tag% and any extra placeholders to a string sourced outside
     * messages.yml (e.g. config.yml's gui.title).
     */
    public String applyPlaceholders(String raw, Map<String, String> replacements) {
        return raw == null ? "" : replace(raw, withDefaults(replacements));
    }

    private Map<String, String> withDefaults(Map<String, String> replacements) {
        Map<String, String> values = new LinkedHashMap<>(replacements);
        values.putIfAbsent("tag", messages.getString("tag", ""));
        return values;
    }

    private void dispatch(String tag, String payload, Collection<? extends CommandSender> recipients) {
        List<Player> players = recipients.stream().filter(Player.class::isInstance)
                .map(Player.class::cast).toList();
        switch (tag) {
            case "message" -> recipients.forEach(recipient -> recipient.sendMessage(Text.parse(payload)));
            case "broadcast" -> plugin.getServer().broadcast(Text.parse(payload));
            case "actionbar" -> players.forEach(player -> player.sendActionBar(Text.parse(payload)));
            case "title" -> showTitle(players, payload);
            case "sound" -> playSound(players, payload);
            case "particle", "particles" -> spawnParticles(players, payload);
            case "firework" -> spawnFireworks(players, payload);
            case "bossbar" -> showBossBar(players, payload);
            default -> throw new IllegalArgumentException("unknown action tag");
        }
    }

    private void showTitle(List<Player> players, String payload) {
        String[] parts = fields(payload);
        if (parts.length < 1 || parts[0].isBlank()) {
            throw new IllegalArgumentException("title text is required");
        }
        Component title = Text.parse(parts[0]);
        Component subtitle = Text.parse(parts.length > 1 ? parts[1] : "");
        int fadeIn = integer(parts, 2, 10);
        int stay = integer(parts, 3, 60);
        int fadeOut = integer(parts, 4, 10);
        Title rendered = Title.title(title, subtitle, Title.Times.times(
                ticks(fadeIn), ticks(stay), ticks(fadeOut)));
        players.forEach(player -> player.showTitle(rendered));
    }

    private void playSound(List<Player> players, String payload) {
        String[] parts = words(payload);
        if (parts.length == 0) {
            throw new IllegalArgumentException("sound name is required");
        }
        Sound sound = sounds.resolve(parts[0]);
        if (sound == null) {
            throw new IllegalArgumentException("unknown sound " + parts[0]);
        }
        float volume = decimal(parts, 1, 1.0F);
        float pitch = decimal(parts, 2, 1.0F);
        players.forEach(player -> player.playSound(player.getLocation(), sound, volume, pitch));
    }

    private void spawnParticles(List<Player> players, String payload) {
        String[] parts = words(payload);
        if (parts.length == 0) {
            throw new IllegalArgumentException("particle name is required");
        }
        Particle particle = particles.resolve(parts[0]);
        if (particle == null) {
            throw new IllegalArgumentException("unknown particle " + parts[0]);
        }
        int count = integer(parts, 1, 1);
        double x = decimal(parts, 2, 0.0F);
        double y = decimal(parts, 3, 0.0F);
        double z = decimal(parts, 4, 0.0F);
        double speed = decimal(parts, 5, 0.0F);
        players.forEach(player -> player.spawnParticle(particle,
                player.getLocation().add(0, 1, 0), count, x, y, z, speed));
    }

    private void spawnFireworks(List<Player> players, String payload) {
        String[] parts = fields(payload);
        List<Color> colors = parseColors(parts.length > 0 ? parts[0] : "#F7A48D,#FFFFFF");
        FireworkEffect.Type type = enumValue(FireworkEffect.Type.class,
                parts.length > 1 ? parts[1] : "BALL_LARGE");
        int power = Math.clamp(integer(parts, 2, 1), 0, 2);
        int count = Math.max(0, integer(parts, 3, 1));
        int gap = Math.max(0, integer(parts, 4, 10));
        for (int index = 0; index < count; index++) {
            long delay = (long) index * gap;
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> launchPoints(players)
                    .forEach(location -> launch(location, colors, type, power)), delay);
        }
    }

    /**
     * One launch point per cluster of online recipients: a player within
     * {@link #FIREWORK_CLUSTER_RADIUS} blocks of an already chosen point in the same
     * world shares it, and a round never exceeds {@link #MAX_FIREWORK_LAUNCH_POINTS}.
     */
    static List<Location> launchPoints(List<Player> players) {
        double radiusSquared = FIREWORK_CLUSTER_RADIUS * FIREWORK_CLUSTER_RADIUS;
        List<Location> points = new ArrayList<>();
        for (Player player : players) {
            if (points.size() >= MAX_FIREWORK_LAUNCH_POINTS) {
                break;
            }
            if (!player.isOnline()) {
                continue;
            }
            Location location = player.getLocation();
            World world = location.getWorld();
            boolean covered = points.stream().anyMatch(point -> Objects.equals(point.getWorld(), world)
                    && point.distanceSquared(location) <= radiusSquared);
            if (!covered) {
                points.add(location);
            }
        }
        return points;
    }

    private void showBossBar(List<Player> players, String payload) {
        String[] parts = fields(payload);
        if (parts.length == 0 || parts[0].isBlank()) {
            throw new IllegalArgumentException("bossbar text is required");
        }
        int seconds = Math.max(1, integer(parts, 1, 5));
        BossBar.Color color = enumValue(BossBar.Color.class, parts.length > 2 ? parts[2] : "PINK");
        BossBar.Overlay overlay = enumValue(BossBar.Overlay.class, parts.length > 3 ? parts[3] : "PROGRESS");
        float progress = Math.clamp(decimal(parts, 4, 1.0F), 0.0F, 1.0F);
        BossBar bossBar = BossBar.bossBar(Text.parse(parts[0]), progress, color, overlay);
        activeBossBars.put(bossBar, List.copyOf(players));
        players.forEach(player -> player.showBossBar(bossBar));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> hideBossBar(bossBar), seconds * 20L);
    }

    private void launch(Location location, List<Color> colors, FireworkEffect.Type type, int power) {
        Firework spawned = location.getWorld().spawn(location.clone().add(0, 1, 0), Firework.class, firework -> {
            firework.getPersistentDataContainer().set(CELEBRATION, PersistentDataType.BYTE, (byte) 1);
            FireworkMeta meta = firework.getFireworkMeta();
            meta.addEffect(FireworkEffect.builder().with(type).withColor(colors).trail(true).flicker(true).build());
            meta.setPower(power);
            firework.setFireworkMeta(meta);
        });
        // A cancelled spawn never enters the world and never fires EntityRemoveEvent,
        // so only track fireworks that actually exist.
        if (spawned != null && spawned.isValid()) {
            activeFireworks.add(spawned);
        }
    }

    private void hideBossBar(BossBar bossBar) {
        List<Player> viewers = activeBossBars.remove(bossBar);
        if (viewers != null) {
            viewers.forEach(player -> player.hideBossBar(bossBar));
        }
    }

    public void shutdown() {
        new ArrayList<>(activeBossBars.keySet()).forEach(this::hideBossBar);
        new ArrayList<>(activeFireworks).forEach(Firework::remove);
        activeFireworks.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCelebrationDamage(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Firework firework
                && firework.getPersistentDataContainer().has(CELEBRATION, PersistentDataType.BYTE)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityRemove(EntityRemoveEvent event) {
        if (event.getEntity() instanceof Firework firework) {
            activeFireworks.remove(firework);
        }
    }

    private static List<Color> parseColors(String payload) {
        List<Color> result = new ArrayList<>();
        for (String configured : payload.split(",")) {
            String hex = configured.strip().replaceFirst("^#", "");
            if (!hex.matches("[0-9a-fA-F]{6}")) {
                throw new IllegalArgumentException("firework colors must be six-digit hex values");
            }
            result.add(Color.fromRGB(Integer.parseInt(hex, 16)));
        }
        return result;
    }

    private static Sound lookupSound(String configured) {
        NamespacedKey direct = minecraftKey(configured);
        Sound sound = direct == null ? null : Registry.SOUNDS.get(direct);
        if (sound != null) {
            return sound;
        }
        String legacy = configured.toUpperCase(Locale.ROOT);
        return Registry.SOUNDS.keyStream().filter(key -> legacyName(key).equals(legacy))
                .findFirst().map(Registry.SOUNDS::get).orElse(null);
    }

    private static Particle lookupParticle(String configured) {
        NamespacedKey direct = minecraftKey(configured);
        Particle particle = direct == null ? null : Registry.PARTICLE_TYPE.get(direct);
        if (particle != null) {
            return particle;
        }
        String legacy = configured.toUpperCase(Locale.ROOT);
        return Registry.PARTICLE_TYPE.keyStream().filter(key -> legacyName(key).equals(legacy))
                .findFirst().map(Registry.PARTICLE_TYPE::get).orElse(null);
    }

    private static ParsedAction parse(String configured) {
        int closing = configured.indexOf(']');
        if (!configured.startsWith("[") || closing < 2) {
            return null;
        }
        return new ParsedAction(configured.substring(1, closing).toLowerCase(Locale.ROOT),
                configured.substring(closing + 1).stripLeading());
    }

    /**
     * Single left-to-right pass: only %key% tokens written in the template are
     * substituted, so a value containing %tag% (or any other token) is inserted
     * literally. Unknown tokens and stray percent signs, such as the trailing one
     * in "%percent%%", are kept as written.
     */
    static String replace(String input, Map<String, String> replacements) {
        StringBuilder result = new StringBuilder(input.length());
        int index = 0;
        while (index < input.length()) {
            int start = input.indexOf('%', index);
            if (start < 0) {
                break;
            }
            result.append(input, index, start);
            int end = input.indexOf('%', start + 1);
            if (end < 0) {
                index = start;
                break;
            }
            String key = input.substring(start + 1, end);
            if (replacements.containsKey(key)) {
                result.append(replacements.get(key));
                index = end + 1;
            } else {
                // Keep the '%' and let the closing one start the next token.
                result.append('%');
                index = start + 1;
            }
        }
        return result.append(input, index, input.length()).toString();
    }

    private static String[] words(String input) {
        String stripped = input.strip();
        return stripped.isEmpty() ? new String[0] : stripped.split("\\s+");
    }

    private static String[] fields(String input) {
        return input.split("\\s*\\|\\s*", -1);
    }

    private static int integer(String[] values, int index, int fallback) {
        if (index >= values.length) {
            return fallback;
        }
        try {
            return Math.max(0, Integer.parseInt(values[index].strip()));
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static float decimal(String[] values, int index, float fallback) {
        if (index >= values.length) {
            return fallback;
        }
        try {
            float value = Float.parseFloat(values[index].strip());
            return Float.isFinite(value) ? value : fallback;
        } catch (NumberFormatException exception) {
            return fallback;
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try {
            return Enum.valueOf(type, value.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("unknown " + type.getSimpleName() + " " + value);
        }
    }

    private static NamespacedKey minecraftKey(String value) {
        String lower = value.toLowerCase(Locale.ROOT);
        return NamespacedKey.fromString(lower.contains(":") ? lower : "minecraft:" + lower);
    }

    private static String legacyName(NamespacedKey key) {
        return key.getKey().toUpperCase(Locale.ROOT).replace('.', '_').replace('/', '_');
    }

    private static Duration ticks(int ticks) {
        return Duration.ofMillis(Math.max(0, ticks) * 50L);
    }

    private void warn(String key, String action) {
        plugin.getLogger().warning("Invalid message action in '" + key + "': " + action);
    }

    private record ParsedAction(String tag, String payload) {
    }

    /**
     * Remembers registry lookups per configured name, including misses. Legacy
     * names such as ENTITY_VILLAGER_NO fall back to scanning the whole registry,
     * which is too slow to repeat on every action. Cleared when messages reload.
     */
    static final class ResolutionCache<T> {
        private final Map<String, Optional<T>> resolved = new ConcurrentHashMap<>();
        private final Function<String, T> lookup;

        ResolutionCache(Function<String, T> lookup) {
            this.lookup = lookup;
        }

        T resolve(String configured) {
            return resolved.computeIfAbsent(configured, name -> Optional.ofNullable(lookup.apply(name)))
                    .orElse(null);
        }

        void clear() {
            resolved.clear();
        }
    }
}
