package dev.everyblock.listener;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.gui.BlockGuiHolder;
import dev.everyblock.gui.CategoryGuiHolder;
import dev.everyblock.gui.EveryBlockMenu;
import dev.everyblock.gui.ItemGuiHolder;
import dev.everyblock.gui.RecentGuiHolder;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongSupplier;

public final class GuiListener implements Listener {
    static final long CLICK_COOLDOWN_NANOS = 200_000_000L;

    private final EveryBlockPlugin plugin;
    private final LongSupplier clock;
    private final Map<UUID, Long> lastNavigation = new HashMap<>();

    public GuiListener(EveryBlockPlugin plugin) {
        this(plugin, System::nanoTime);
    }

    GuiListener(EveryBlockPlugin plugin, LongSupplier clock) {
        this.plugin = plugin;
        this.clock = clock;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder(false) instanceof EveryBlockMenu holder)) {
            return;
        }
        event.setCancelled(true);
        if (event.getWhoClicked() instanceof Player player
                && event.getRawSlot() >= 0
                && event.getRawSlot() < event.getView().getTopInventory().getSize()
                && claimNavigation(player.getUniqueId())) {
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
        if (event.getView().getTopInventory().getHolder(false) instanceof EveryBlockMenu) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        lastNavigation.remove(event.getPlayer().getUniqueId());
    }

    private boolean claimNavigation(UUID playerId) {
        long now = clock.getAsLong();
        Long last = lastNavigation.get(playerId);
        if (last != null && now - last < CLICK_COOLDOWN_NANOS) {
            return false;
        }
        lastNavigation.put(playerId, now);
        return true;
    }
}
