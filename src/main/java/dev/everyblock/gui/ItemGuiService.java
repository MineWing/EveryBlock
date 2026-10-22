package dev.everyblock.gui;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.util.Text;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class ItemGuiService {
    private static final int PAGE_SIZE = 45;
    private final EveryBlockPlugin plugin;
    private final MenuItems items;
    private final CoalescedRefresh refresher;
    private volatile SortedItems sorted;

    public ItemGuiService(EveryBlockPlugin plugin) {
        this.plugin = plugin;
        this.items = new MenuItems(plugin);
        this.refresher = new CoalescedRefresh(plugin, this::refreshNow);
    }

    public void open(Player player, int requestedPage, BlockFilter filter) {
        open(player, requestedPage, filter, true);
    }

    private void open(Player player, int requestedPage, BlockFilter filter, boolean playEffect) {
        if (!checkAccess(player)) {
            return;
        }
        List<Material> visible = new ArrayList<>();
        for (Material material : sortedItems()) {
            boolean matches = switch (filter) {
                case ALL -> true;
                case FOUND -> plugin.itemProgress().found(material);
                case MISSING -> !plugin.itemProgress().found(material);
            };
            if (matches) {
                visible.add(material);
            }
        }
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
        ItemStack filler = MenuItems.filler();
        for (int slot = 45; slot < 54; slot++) {
            inventory.setItem(slot, filler);
        }
        if (page > 0) {
            inventory.setItem(45, items.pageButton("gui.buttons.previous", page));
        }
        inventory.setItem(47, items.filterToggle(filter));
        inventory.setItem(49, items.progress(Material.NETHER_STAR, "gui.buttons.item-progress.name",
                plugin.itemProgress().collectedCount(), plugin.itemProgress().totalCount()));
        inventory.setItem(51, items.button(Material.BARRIER, "gui.buttons.close"));
        if (page + 1 < pages) {
            inventory.setItem(53, items.pageButton("gui.buttons.next", page + 2));
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
            case 45 -> {
                if (MenuItems.isArrow(player, slot)) {
                    open(player, holder.page() - 1, holder.filter(), false);
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            case 47 -> open(player, 0, holder.filter().next());
            case 51 -> player.closeInventory();
            case 53 -> {
                if (MenuItems.isArrow(player, slot)) {
                    open(player, holder.page() + 1, holder.filter(), false);
                    plugin.messages().send(player, "gui-next-page");
                }
            }
            default -> {
            }
        }
    }

    /** Re-renders open item menus, coalescing bursts into at most one pass per second. */
    public void refreshOpenMenus() {
        refresher.request();
    }

    private void refreshNow() {
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
        return items.collectible(material, plugin.itemProgress().found(material)
                ? plugin.itemProgress().discovery(material) : null, false, "gui.collectible.bring-item");
    }

    private List<Material> sortedItems() {
        List<Material> source = plugin.itemCatalogue().items();
        SortedItems current = sorted;
        if (current == null || current.source() != source) {
            current = SortedItems.of(source);
            sorted = current;
        }
        return current.materials();
    }

    private record SortedItems(List<Material> source, List<Material> materials) {
        static SortedItems of(List<Material> source) {
            record Entry(Material material, String name) {
            }
            List<Material> materials = source.stream()
                    .map(material -> new Entry(material, Text.pretty(material.name())))
                    .sorted(Comparator.comparing(Entry::name))
                    .map(Entry::material)
                    .toList();
            return new SortedItems(source, materials);
        }
    }
}
