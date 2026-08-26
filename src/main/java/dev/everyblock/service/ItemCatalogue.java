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

public final class ItemCatalogue {
    private static final Set<String> ITEM_ONLY_UNOBTAINABLE = Set.of(
            "COMMAND_BLOCK_MINECART",
            "DEBUG_STICK",
            "KNOWLEDGE_BOOK"
    );

    private final JavaPlugin plugin;
    private List<Material> items = List.of();
    private Set<Material> itemSet = Set.of();

    public ItemCatalogue(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void rebuild(PluginSettings settings) {
        Set<String> excluded = settings.excludedItems();
        List<String> prefixes = settings.excludedItemPrefixes();
        Set<String> included = settings.includedItems();
        LinkedHashSet<Material> selected = new LinkedHashSet<>();

        Arrays.stream(Material.values())
                .filter(material -> !material.isLegacy())
                .filter(BlockCatalogue::nonAir)
                .filter(Material::isItem)
                .filter(material -> survivalObtainable(material.name()))
                .filter(material -> included.contains(material.name())
                        || !BlockCatalogue.excluded(material.name(), excluded, prefixes))
                .forEach(selected::add);

        for (String name : included) {
            Material material = Material.matchMaterial(name);
            if (material == null || material.isLegacy() || !material.isItem()
                    || !BlockCatalogue.nonAir(material) || !survivalObtainable(material.name())) {
                if (plugin != null) {
                    plugin.getLogger().warning("Ignoring invalid items.included entry: " + name);
                }
            } else {
                selected.add(material);
            }
        }

        items = selected.stream()
                .sorted(Comparator.comparing(material -> Text.pretty(material.name())))
                .toList();
        itemSet = Set.copyOf(items);
        if (plugin != null) {
            plugin.getLogger().info("Every Item catalogue contains " + items.size() + " obtainable items."
                    + (settings.itemsEnabled() ? "" : " Tracking is disabled."));
        }
    }

    public List<Material> items() {
        return items;
    }

    public boolean contains(Material material) {
        return itemSet.contains(material);
    }

    public Material match(String input) {
        if (input == null) {
            return null;
        }
        Material material = Material.matchMaterial(input.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
        return material != null && contains(material) ? material : null;
    }

    static boolean survivalObtainable(String name) {
        return BlockCatalogue.survivalObtainable(name)
                && !ITEM_ONLY_UNOBTAINABLE.contains(name)
                && !name.endsWith("_SPAWN_EGG")
                && !name.startsWith("TEST_");
    }
}
