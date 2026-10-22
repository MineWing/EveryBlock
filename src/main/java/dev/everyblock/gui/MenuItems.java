package dev.everyblock.gui;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.model.Discovery;
import dev.everyblock.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Item builders shared by the block and item menus. */
final class MenuItems {
    private static final String FALLBACK_DATE_FORMAT = "dd MMM yyyy, HH:mm";

    private final EveryBlockPlugin plugin;
    private volatile CachedFormatter dateFormatter;

    MenuItems(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Builds a collectible tile. {@code discovery} is null while the material is still missing;
     * {@code bringKey} names the hint shown in that case.
     */
    ItemStack collectible(Material material, Discovery discovery, boolean showRarity, String bringKey) {
        boolean found = discovery != null;
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            // Paper exposes a handful of technical materials as item/block-like. The
            // catalogues filter them, but this fallback keeps the GUI safe if a future
            // Minecraft version adds another unusual material.
            item = new ItemStack(Material.PAPER);
            meta = Objects.requireNonNull(item.getItemMeta());
        }
        String name = Text.escape(Text.pretty(material.name()));
        BlockRarity rarity = BlockRarity.of(material);
        Component itemName;
        if (rarity == BlockRarity.EPIC || rarity == BlockRarity.LEGENDARY) {
            String prefixKey = found ? "gui.collectible.collected-prefix" : "gui.collectible.missing-prefix";
            String prefix = plugin.messages().string(prefixKey, Map.of(), "");
            itemName = Text.parse(prefix + "<bold>" + rarity.style(name) + "</bold>");
        } else {
            String nameKey = found ? "gui.collectible.collected-name" : "gui.collectible.missing-name";
            itemName = plugin.messages().text(nameKey, Map.of("name", name));
        }
        meta.displayName(itemName);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.empty());
        if (showRarity) {
            lore.add(plugin.messages().text("gui.collectible.rarity-line", Map.of("rarity", rarity.miniMessage())));
            lore.add(Component.empty());
        }
        if (found) {
            lore.add(plugin.messages().text("gui.collectible.collected-header"));
            lore.add(plugin.messages().text("gui.collectible.found-by",
                    Map.of("player", Text.escape(discovery.playerName()))));
            lore.add(plugin.messages().text("gui.collectible.discovered", Map.of("date", formatDate(discovery))));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(plugin.messages().text("gui.collectible.not-collected-header"));
            lore.add(plugin.messages().text(bringKey));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    ItemStack progress(Material icon, String nameKey, int found, int total) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text(nameKey));
        meta.lore(List.of(
                Component.empty(),
                Text.parse(progressBar(found, total, 20)),
                progressLine(found, total),
                plugin.messages().text("gui.progress.complete-line", Map.of("percent", Text.percent(found, total))),
                remainingLine(found, total)
        ));
        item.setItemMeta(meta);
        return item;
    }

    Component progressLine(int found, int total) {
        return plugin.messages().text("gui.progress.collected-line",
                Map.of("found", Integer.toString(found), "total", Integer.toString(total)));
    }

    Component remainingLine(int found, int total) {
        return plugin.messages().text("gui.progress.remaining-line",
                Map.of("remaining", Integer.toString(Math.max(0, total - found))));
    }

    String progressBar(int found, int total, int width) {
        String filled = plugin.messages().string("gui.progress-bar.filled", Map.of(), "#f7a48d");
        String empty = plugin.messages().string("gui.progress-bar.empty", Map.of(), "dark_gray");
        return Text.progressBar(found, total, width, filled, empty);
    }

    ItemStack filterToggle(BlockFilter active) {
        ItemStack item = new ItemStack(Material.HOPPER);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text("gui.buttons.view-filter.name"));
        meta.lore(List.of(
                Component.empty(),
                filterLine("all", active == BlockFilter.ALL),
                filterLine("found", active == BlockFilter.FOUND),
                filterLine("missing", active == BlockFilter.MISSING),
                Component.empty(),
                plugin.messages().text("gui.buttons.view-filter.click-to-cycle")
        ));
        item.setItemMeta(meta);
        return item;
    }

    private Component filterLine(String filterKey, boolean active) {
        String label = plugin.messages().string("gui.filters." + filterKey, Map.of(), Text.pretty(filterKey));
        String key = active ? "gui.buttons.view-filter.active" : "gui.buttons.view-filter.inactive";
        return plugin.messages().text(key, Map.of("label", label));
    }

    ItemStack button(Material material, String baseKey) {
        return button(material, baseKey, Map.of());
    }

    ItemStack button(Material material, String baseKey, Map<String, String> replacements) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text(baseKey + ".name", replacements));
        List<Component> lore = plugin.messages().lines(baseKey + ".lore", replacements);
        if (!lore.isEmpty()) {
            meta.lore(lore);
        }
        item.setItemMeta(meta);
        return item;
    }

    ItemStack pageButton(String baseKey, int displayPage) {
        return button(Material.ARROW, baseKey, Map.of("page", Integer.toString(displayPage)));
    }

    static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    static boolean isArrow(Player player, int slot) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        return item != null && item.getType() == Material.ARROW;
    }

    String formatDate(Discovery discovery) {
        String pattern = plugin.settings().dateFormat();
        CachedFormatter cached = dateFormatter;
        if (cached == null || !Objects.equals(cached.pattern(), pattern)) {
            cached = new CachedFormatter(pattern, buildFormatter(pattern));
            dateFormatter = cached;
        }
        return cached.formatter().format(discovery.discoveredAt());
    }

    private static DateTimeFormatter buildFormatter(String pattern) {
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(pattern);
        } catch (IllegalArgumentException | NullPointerException exception) {
            formatter = DateTimeFormatter.ofPattern(FALLBACK_DATE_FORMAT);
        }
        return formatter.withZone(ZoneId.systemDefault());
    }

    private record CachedFormatter(String pattern, DateTimeFormatter formatter) {
    }
}
