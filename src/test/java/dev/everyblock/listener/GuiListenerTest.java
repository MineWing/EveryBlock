package dev.everyblock.listener;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.gui.BlockFilter;
import dev.everyblock.gui.BlockCategory;
import dev.everyblock.gui.BlockGuiHolder;
import dev.everyblock.gui.GuiService;
import dev.everyblock.gui.ItemGuiHolder;
import dev.everyblock.gui.ItemGuiService;
import dev.everyblock.message.MessageService;
import org.bukkit.Server;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryView;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class GuiListenerTest {
    private EveryBlockPlugin plugin;
    private Player player;
    private Inventory inventory;
    private InventoryView view;
    private InventoryClickEvent event;
    private BukkitScheduler scheduler;
    private GuiService gui;
    private ItemGuiService itemGui;
    private GuiListener listener;

    @BeforeEach
    void setUp() {
        plugin = mock(EveryBlockPlugin.class);
        Server server = mock(Server.class);
        scheduler = mock(BukkitScheduler.class);
        when(plugin.getServer()).thenReturn(server);
        when(server.getScheduler()).thenReturn(scheduler);
        gui = mock(GuiService.class);
        itemGui = mock(ItemGuiService.class);
        when(plugin.gui()).thenReturn(gui);
        when(plugin.itemGui()).thenReturn(itemGui);
        when(plugin.messages()).thenReturn(mock(MessageService.class));
        when(plugin.settings()).thenReturn(mock(PluginSettings.class));
        player = mock(Player.class);
        inventory = mock(Inventory.class);
        view = mock(InventoryView.class);
        event = mock(InventoryClickEvent.class);
        when(player.isOnline()).thenReturn(true);
        when(player.hasPermission("everyblock.use")).thenReturn(true);
        when(player.hasPermission("everyblock.items")).thenReturn(true);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenReturn(inventory);
        when(inventory.getSize()).thenReturn(54);
        when(event.getView()).thenReturn(view);
        when(event.getWhoClicked()).thenReturn(player);
        when(event.getRawSlot()).thenReturn(47);
        listener = new GuiListener(plugin);
    }

    @Test
    void cancelsClickImmediatelyAndDefersNavigationToScheduler() {
        BlockGuiHolder holder = new BlockGuiHolder(0, BlockFilter.ALL, "stone", BlockCategory.ALL);
        when(inventory.getHolder(false)).thenReturn(holder);
        listener.onClick(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(gui);
        queuedNavigation().run();
        verify(gui).handleClick(player, 47, holder);
    }

    @Test
    void changedInventoryCancelsQueuedNavigation() {
        when(inventory.getHolder(false)).thenReturn(new BlockGuiHolder(0, BlockFilter.ALL, "", BlockCategory.ALL));
        listener.onClick(event);
        when(view.getTopInventory()).thenReturn(mock(Inventory.class));
        queuedNavigation().run();
        verifyNoInteractions(gui);
        verify(player, never()).closeInventory();
    }

    @Test
    void disconnectedPlayerDoesNotNavigate() {
        when(inventory.getHolder(false)).thenReturn(new BlockGuiHolder(0, BlockFilter.ALL, "", BlockCategory.ALL));
        listener.onClick(event);
        when(player.isOnline()).thenReturn(false);
        queuedNavigation().run();
        verifyNoInteractions(gui);
    }

    @Test
    void permissionLossBeforeNextTickClosesMenu() {
        when(inventory.getHolder(false)).thenReturn(new BlockGuiHolder(0, BlockFilter.ALL, "", BlockCategory.ALL));
        listener.onClick(event);
        when(player.hasPermission("everyblock.use")).thenReturn(false);
        queuedNavigation().run();
        verify(player).closeInventory();
        verifyNoInteractions(gui);
    }

    @Test
    void itemDisableBeforeNextTickClosesMenu() {
        when(inventory.getHolder(false)).thenReturn(new ItemGuiHolder(0, BlockFilter.ALL));
        when(plugin.settings().itemsEnabled()).thenReturn(true);
        listener.onClick(event);
        when(plugin.settings().itemsEnabled()).thenReturn(false);
        queuedNavigation().run();
        verify(player).closeInventory();
        verifyNoInteractions(itemGui);
    }

    @Test
    void bottomInventoryClickIsCancelledWithoutNavigation() {
        when(inventory.getHolder(false)).thenReturn(new ItemGuiHolder(0, BlockFilter.ALL));
        when(event.getRawSlot()).thenReturn(54);
        listener.onClick(event);
        verify(event).setCancelled(true);
        verifyNoInteractions(scheduler, itemGui, gui);
    }

    private Runnable queuedNavigation() {
        ArgumentCaptor<Runnable> task = ArgumentCaptor.forClass(Runnable.class);
        verify(scheduler).runTask(eq(plugin), task.capture());
        return task.getValue();
    }
}
