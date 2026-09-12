package dev.everyblock.service;

import dev.everyblock.config.PluginSettings;
import dev.everyblock.util.Text;
import org.bukkit.Material;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class BlockCatalogue {
    private static final Set<String> SURVIVAL_UNOBTAINABLE = Set.of(
            "BARRIER",
            "BEDROCK",
            "BUDDING_AMETHYST",
            "CHAIN_COMMAND_BLOCK",
            "COMMAND_BLOCK",
            "END_PORTAL_FRAME",
            "FROGSPAWN",
            "JIGSAW",
            "LIGHT",
            "PETRIFIED_OAK_SLAB",
            "PLAYER_HEAD",
            "REINFORCED_DEEPSLATE",
            "REPEATING_COMMAND_BLOCK",
            "SPAWNER",
            "STRUCTURE_BLOCK",
            "STRUCTURE_VOID",
            "SUSPICIOUS_GRAVEL",
            "SUSPICIOUS_SAND",
            "TEST_BLOCK",
            "TEST_INSTANCE_BLOCK",
            "TRIAL_SPAWNER",
            "VAULT"
    );
    private final JavaPlugin plugin;
    private volatile List<Material> blocks = List.of();
    private volatile Set<Material> blockSet = Set.of();

    public BlockCatalogue(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void rebuild(PluginSettings settings) {
        Set<String> excluded = settings.excludedBlocks();
        List<String> prefixes = settings.excludedPrefixes();
        Set<String> included = settings.includedBlocks();
        LinkedHashSet<Material> selected = new LinkedHashSet<>();

        Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy())
                .filter(BlockCatalogue::nonAir)
                .filter(Material::isBlock)
                .filter(Material::isItem)
                .filter(material -> survivalObtainable(material.name()))
                .filter(material -> included.contains(material.name()) || !excluded(material.name(), excluded, prefixes))
                .forEach(selected::add);

        for (String name : included) {
            Material material = Material.matchMaterial(name);
            if (material == null || material.isLegacy() || !nonAir(material)
                    || !survivalObtainable(material.name())
                    || !material.isBlock() || !material.isItem()) {
                if (plugin != null) {
                    plugin.getLogger().warning("Ignoring invalid blocks.included entry: " + name);
                }
            } else {
                selected.add(material);
            }
        }

        blocks = selected.stream()
                .sorted(Comparator.comparing(material -> Text.pretty(material.name())))
                .toList();
        blockSet = Set.copyOf(blocks);
        if (plugin != null) {
            plugin.getLogger().info("Every Block catalogue contains " + blocks.size() + " obtainable block items.");
        }
    }

    public List<Material> blocks() {
        return blocks;
    }

    public boolean contains(Material material) {
        return blockSet.contains(material);
    }

    public Material match(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.strip().toUpperCase(Locale.ROOT).replace(' ', '_');
        Material direct = Material.matchMaterial(normalized);
        return direct != null && contains(direct) ? direct : null;
    }

    static boolean excluded(String materialName, Set<String> names, List<String> prefixes) {
        if (names.contains(materialName)) {
            return true;
        }
        return prefixes.stream().anyMatch(materialName::startsWith);
    }

    static boolean nonAir(Material material) {
        return !material.name().equals("AIR")
                && !material.name().equals("CAVE_AIR")
                && !material.name().equals("VOID_AIR");
    }

    static boolean survivalObtainable(String materialName) {
        return !SURVIVAL_UNOBTAINABLE.contains(materialName)
                && !materialName.startsWith("INFESTED_");
    }
}
