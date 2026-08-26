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
            if (holder instanceof BlockGuiHolder blockHolder) {
                plugin.gui().handleClick(player, event.getRawSlot(), blockHolder);
            } else if (holder instanceof RecentGuiHolder recentHolder) {
                plugin.gui().handleRecentClick(player, event.getRawSlot(), recentHolder);
            } else if (holder instanceof ItemGuiHolder itemHolder) {
                plugin.itemGui().handleClick(player, event.getRawSlot(), itemHolder);
            } else if (!(holder instanceof RecipeGuiHolder)) {
                plugin.gui().handleCategoryClick(player, event.getRawSlot());
            }
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
