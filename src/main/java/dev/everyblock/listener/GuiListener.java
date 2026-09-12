package dev.everyblock.listener;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.gui.BlockGuiHolder;
import dev.everyblock.gui.CategoryGuiHolder;
import dev.everyblock.gui.RecentGuiHolder;
import dev.everyblock.gui.ItemGuiHolder;
import dev.everyblock.gui.RecipeGuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;

public final class GuiListener implements Listener {
    private final EveryBlockPlugin plugin;

    public GuiListener(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Object holder = event.getView().getTopInventory().getHolder(false);
        if (!(holder instanceof BlockGuiHolder) && !(holder instanceof CategoryGuiHolder)
                && !(holder instanceof RecentGuiHolder) && !(holder instanceof ItemGuiHolder)
                && !(holder instanceof RecipeGuiHolder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player
                && event.getRawSlot() >= 0
                && event.getRawSlot() < event.getView().getTopInventory().getSize()) {
            Inventory expected = event.getView().getTopInventory();
            int rawSlot = event.getRawSlot();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline() || player.getOpenInventory().getTopInventory() != expected) {
                    return;
                }
                boolean items = holder instanceof ItemGuiHolder;
                if (!player.hasPermission(items ? "everyblock.items" : "everyblock.use")) {
                    player.closeInventory();
                    plugin.messages().send(player, "no-permission");
                    return;
                }
                if (items && !plugin.settings().itemsEnabled()) {
                    player.closeInventory();
                    plugin.messages().send(player, "items-disabled");
                    return;
                }
                if (holder instanceof BlockGuiHolder blockHolder) {
                    plugin.gui().handleClick(player, rawSlot, blockHolder);
                } else if (holder instanceof RecentGuiHolder recentHolder) {
                    plugin.gui().handleRecentClick(player, rawSlot, recentHolder);
                } else if (holder instanceof ItemGuiHolder itemHolder) {
                    plugin.itemGui().handleClick(player, rawSlot, itemHolder);
                } else if (holder instanceof CategoryGuiHolder) {
                    plugin.gui().handleCategoryClick(player, rawSlot);
                }
            });
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        Object holder = event.getView().getTopInventory().getHolder(false);
        if (holder instanceof BlockGuiHolder || holder instanceof CategoryGuiHolder
                || holder instanceof RecentGuiHolder || holder instanceof ItemGuiHolder
                || holder instanceof RecipeGuiHolder) {
            event.setCancelled(true);
        }
    }
}
