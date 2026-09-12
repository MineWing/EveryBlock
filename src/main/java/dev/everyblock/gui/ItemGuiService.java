package dev.everyblock.gui;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.model.Discovery;
import dev.everyblock.util.Text;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class ItemGuiService {
    private static final int PAGE_SIZE = 45;
    private final EveryBlockPlugin plugin;

    public ItemGuiService(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void open(Player player, int requestedPage, BlockFilter filter) {
        open(player, requestedPage, filter, true);
    }

    private void open(Player player, int requestedPage, BlockFilter filter, boolean playEffect) {
        if (!checkAccess(player)) {
            return;
        }
        List<Material> visible = plugin.itemCatalogue().items().stream()
                .filter(material -> switch (filter) {
                    case ALL -> true;
                    case FOUND -> plugin.itemProgress().found(material);
                    case MISSING -> !plugin.itemProgress().found(material);
                })
                .sorted(Comparator.comparing(material -> Text.pretty(material.name())))
                .toList();
        int pages = Math.max(1, (visible.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        int page = Math.clamp(requestedPage, 0, pages - 1);
        ItemGuiHolder holder = new ItemGuiHolder(page, filter);
        Inventory inventory = Bukkit.createInventory(holder, 54,
                plugin.messages().text("gui.block-menu.items-title",
                        Map.of("page", Integer.toString(page + 1), "pages", Integer.toString(pages))));
        holder.inventory(inventory);

        int start = page * PAGE_SIZE;
        for (int index = start; index < Math.min(visible.size(), start + PAGE_SIZE); index++) {
            inventory.setItem(index - start, item(visible.get(index)));
        }
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler());
        }
        if (page > 0) {
            inventory.setItem(45, pageButton(Material.ARROW, "gui.buttons.previous", page));
        }
        inventory.setItem(47, filterToggle(filter));
        inventory.setItem(49, progress());
        inventory.setItem(51, button(Material.BARRIER, "gui.buttons.close"));
        if (page + 1 < pages) {
            inventory.setItem(53, pageButton(Material.ARROW, "gui.buttons.next", page + 2));
        }
        player.openInventory(inventory);
        if (playEffect) {
            plugin.messages().send(player, "gui-open");
        }
    }

    public void handleClick(Player player, int slot, ItemGuiHolder holder) {
        if (!checkAccess(player)) {
            return;
        }
        switch (slot) {
            case 45 -> open(player, holder.page() - 1, holder.filter());
            case 47 -> open(player, 0, holder.filter().next());
            case 51 -> player.closeInventory();
            case 53 -> {
                if (isArrow(player, slot)) {
                    open(player, holder.page() + 1, holder.filter(), false);
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            default -> {
            }
        }
    }

    public void refreshOpenMenus() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof ItemGuiHolder holder) {
                open(player, holder.page(), holder.filter(), false);
            }
        }
    }

    private boolean checkAccess(Player player) {
        String denial = !player.hasPermission("everyblock.items") ? "no-permission"
                : !plugin.settings().itemsEnabled() ? "items-disabled" : null;
        if (denial == null) {
            return true;
        }
        if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof ItemGuiHolder) {
            player.closeInventory();
        }
        plugin.messages().send(player, denial);
        return false;
    }

    private ItemStack item(Material material) {
        boolean found = plugin.itemProgress().found(material);
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
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
        if (found) {
            Discovery discovery = plugin.itemProgress().discovery(material);
            lore.add(plugin.messages().text("gui.collectible.collected-header"));
            lore.add(plugin.messages().text("gui.collectible.found-by",
                    Map.of("player", Text.escape(discovery.playerName()))));
            lore.add(plugin.messages().text("gui.collectible.discovered", Map.of("date", formatDate(discovery))));
            meta.addEnchant(Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
        } else {
            lore.add(plugin.messages().text("gui.collectible.not-collected-header"));
            lore.add(plugin.messages().text("gui.collectible.bring-item"));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack progress() {
        int found = plugin.itemProgress().collectedCount();
        int total = plugin.itemProgress().totalCount();
        ItemStack item = new ItemStack(Material.NETHER_STAR);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(plugin.messages().text("gui.buttons.item-progress.name"));
        String filled = plugin.messages().string("gui.progress-bar.filled", Map.of(), "#f7a48d");
        String empty = plugin.messages().string("gui.progress-bar.empty", Map.of(), "dark_gray");
        meta.lore(List.of(
                Component.empty(),
                Text.parse(Text.progressBar(found, total, 20, filled, empty)),
                plugin.messages().text("gui.progress.collected-line",
                        Map.of("found", Integer.toString(found), "total", Integer.toString(total))),
                plugin.messages().text("gui.progress.complete-line", Map.of("percent", Text.percent(found, total))),
                plugin.messages().text("gui.progress.remaining-line",
                        Map.of("remaining", Integer.toString(Math.max(0, total - found))))
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack filterToggle(BlockFilter active) {
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

    private ItemStack button(Material material, String baseKey) {
        return button(material, baseKey, Map.of());
    }

    private ItemStack button(Material material, String baseKey, Map<String, String> replacements) {
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

    private ItemStack pageButton(Material material, String baseKey, int displayPage) {
        return button(material, baseKey, Map.of("page", Integer.toString(displayPage)));
    }

    private static ItemStack filler() {
        ItemStack item = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private static boolean isArrow(Player player, int slot) {
        ItemStack item = player.getOpenInventory().getTopInventory().getItem(slot);
        return item != null && item.getType() == Material.ARROW;
    }

    private String formatDate(Discovery discovery) {
        DateTimeFormatter formatter;
        try {
            formatter = DateTimeFormatter.ofPattern(plugin.settings().dateFormat());
        } catch (IllegalArgumentException exception) {
            formatter = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");
        }
        return formatter.withZone(ZoneId.systemDefault()).format(discovery.discoveredAt());
    }
}
