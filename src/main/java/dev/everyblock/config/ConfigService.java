package dev.everyblock.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class ConfigService {
    private final JavaPlugin plugin;
    private PluginSettings settings;

    public ConfigService(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.saveDefaultConfig();
        File messages = new File(plugin.getDataFolder(), "messages.yml");
        if (!messages.isFile()) {
            plugin.saveResource("messages.yml", false);
        }
        YamlConfiguration config = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "config.yml"));

        PluginSettings.ParticipantMode mode;
        try {
            mode = PluginSettings.ParticipantMode.valueOf(
                    config.getString("participants.mode", "EVERYONE").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            plugin.getLogger().warning("Invalid participants.mode; using EVERYONE.");
            mode = PluginSettings.ParticipantMode.EVERYONE;
        }

        Set<UUID> allowlist = new LinkedHashSet<>();
        for (String configured : config.getStringList("participants.allowlist")) {
            try {
                allowlist.add(UUID.fromString(configured));
            } catch (IllegalArgumentException exception) {
                plugin.getLogger().warning("Ignoring invalid participant UUID: " + configured);
            }
        }

        String databaseFile = config.getString("storage.database-file", "progress.db");
        if (databaseFile == null || databaseFile.isBlank()) {
            databaseFile = "progress.db";
        }
        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path databasePath = dataFolder.resolve(databaseFile).normalize();
        if (!databasePath.startsWith(dataFolder)) {
            throw new IllegalArgumentException("storage.database-file must remain inside the plugin folder");
        }

        List<Integer> milestones = new ArrayList<>();
        for (int milestone : config.getIntegerList("celebration.milestones")) {
            if (milestone > 0 && milestone < 100 && !milestones.contains(milestone)) {
                milestones.add(milestone);
            }
        }
        milestones.sort(Integer::compareTo);

        settings = new PluginSettings(
                mode,
                Set.copyOf(allowlist),
                Math.clamp(config.getInt("counting.scan-interval-ticks", 10), 1, 1_200),
                config.getBoolean("counting.count-creative-mode", false),
                uppercaseSet(config.getStringList("blocks.excluded")),
                uppercaseList(config.getStringList("blocks.excluded-prefixes")),
                uppercaseSet(config.getStringList("blocks.included")),
                config.getBoolean("items.enabled", false),
                uppercaseSet(config.getStringList("items.excluded")),
                uppercaseList(config.getStringList("items.excluded-prefixes")),
                uppercaseSet(config.getStringList("items.included")),
                databasePath,
                config.getString("gui.title",
                        "<#f7a48d><bold>%category%</bold></#f7a48d> <dark_gray>•</dark_gray> <gray>%page%/%pages%</gray>"),
                config.getString("gui.date-format", "dd MMM yyyy, HH:mm"),
                List.copyOf(milestones)
        );
    }

    public PluginSettings settings() {
        return settings;
    }

    private static Set<String> uppercaseSet(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>(uppercaseList(values));
        return Set.copyOf(result);
    }

    private static List<String> uppercaseList(List<String> values) {
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.strip().toUpperCase(Locale.ROOT))
                .distinct()
                .toList();
    }
}
