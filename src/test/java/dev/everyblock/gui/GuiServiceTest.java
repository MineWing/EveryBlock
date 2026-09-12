package dev.everyblock.gui;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.message.MessageService;
import dev.everyblock.service.BlockCatalogue;
import dev.everyblock.service.ItemCatalogue;
import dev.everyblock.service.ItemProgressService;
import dev.everyblock.service.ProgressService;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class GuiServiceTest {
    private EveryBlockPlugin plugin;
    private Player player;
    private InventoryView view;
    private Inventory current;
    private final List<InventoryHolder> rendered = new ArrayList<>();

    @BeforeEach
    void setUp() {
        plugin = mock(EveryBlockPlugin.class);
        PluginSettings settings = mock(PluginSettings.class);
        when(plugin.settings()).thenReturn(settings);
        when(settings.guiTitle()).thenReturn("Blocks");
        when(settings.itemsEnabled()).thenReturn(true);
        MessageService messages = mock(MessageService.class);
        when(plugin.messages()).thenReturn(messages);
        when(messages.text(anyString())).thenReturn(Component.empty());
        when(messages.text(anyString(), anyMap())).thenReturn(Component.empty());
        when(messages.lines(anyString())).thenReturn(List.of());
        when(messages.lines(anyString(), anyMap())).thenReturn(List.of());
        when(messages.string(anyString(), anyMap(), anyString())).thenAnswer(call -> call.getArgument(2));
        when(messages.applyPlaceholders(anyString(), anyMap())).thenAnswer(call -> call.getArgument(0));
        when(plugin.catalogue()).thenReturn(mock(BlockCatalogue.class));
        when(plugin.progress()).thenReturn(mock(ProgressService.class));
        when(plugin.itemCatalogue()).thenReturn(mock(ItemCatalogue.class));
        when(plugin.itemProgress()).thenReturn(mock(ItemProgressService.class));
        player = mock(Player.class);
        view = mock(InventoryView.class);
        current = mock(Inventory.class);
        when(player.getOpenInventory()).thenReturn(view);
        when(view.getTopInventory()).thenAnswer(call -> current);
        when(player.hasPermission(anyString())).thenReturn(true);
        when(player.openInventory(any(Inventory.class))).thenAnswer(call -> {
            current = call.getArgument(0);
            return view;
        });
    }

    @Test
    void emptySearchCanCycleFoundThenMissingAndRefreshReplacesStaleContents() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedConstruction<ItemStack> stacks = mockItems()) {
            stubInventories(bukkit);
            GuiService gui = new GuiService(plugin);
            when(plugin.catalogue().blocks()).thenReturn(List.of(Material.STONE));
            assertTrue(gui.open(player, 4, BlockFilter.ALL, "stone"));
            BlockGuiHolder all = (BlockGuiHolder) current.getHolder(false);
            verify(current).setItem(eq(0), any(ItemStack.class));
            gui.handleClick(player, 47, all);
            BlockGuiHolder found = (BlockGuiHolder) current.getHolder(false);
            assertEquals(BlockFilter.FOUND, found.filter());
            assertEquals("stone", found.query());
            assertEquals(0, found.page());
            verify(current, never()).setItem(eq(0), any(ItemStack.class));
            gui.handleClick(player, 47, found);
            BlockGuiHolder missing = (BlockGuiHolder) current.getHolder(false);
            assertEquals(BlockFilter.MISSING, missing.filter());
            verify(current).setItem(eq(0), any(ItemStack.class));
            Inventory beforeRefresh = current;
            when(plugin.progress().found(Material.STONE)).thenReturn(true);
            gui.refreshOpenMenus();
            assertNotSame(beforeRefresh, current);
            verify(current, never()).setItem(eq(0), any(ItemStack.class));
            assertEquals(BlockFilter.MISSING, ((BlockGuiHolder) current.getHolder(false)).filter());
            assertEquals("stone", ((BlockGuiHolder) current.getHolder(false)).query());
        }
    }

    @Test
    void itemReloadRefreshesEnabledMenuAndClosesWhenDisabled() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class);
             MockedConstruction<ItemStack> stacks = mockItems()) {
            stubInventories(bukkit);
            when(current.getHolder(false)).thenReturn(new ItemGuiHolder(3, BlockFilter.MISSING));
            ItemGuiService gui = new ItemGuiService(plugin);
            gui.refreshOpenMenus();
            ItemGuiHolder refreshed = (ItemGuiHolder) current.getHolder(false);
            assertEquals(BlockFilter.MISSING, refreshed.filter());
            assertEquals(0, refreshed.page());
            verify(player).openInventory(current);
            when(plugin.settings().itemsEnabled()).thenReturn(false);
            gui.refreshOpenMenus();
            verify(player).closeInventory();
            assertEquals(1, rendered.size());
        }
    }

    @Test
    void itemPermissionRevocationClosesExistingMenu() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            when(current.getHolder(false)).thenReturn(new ItemGuiHolder(0, BlockFilter.ALL));
            when(player.hasPermission("everyblock.items")).thenReturn(false);
            new ItemGuiService(plugin).refreshOpenMenus();
            verify(player).closeInventory();
            verify(player, never()).openInventory(any(Inventory.class));
        }
    }

    @Test
    void disableClosesAllOwnedMenuTypesAndLeavesOtherInventoriesOpen() {
        try (MockedStatic<Bukkit> bukkit = mockStatic(Bukkit.class)) {
            bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
            GuiService gui = new GuiService(plugin);
            List<InventoryHolder> holders = List.of(
                    new BlockGuiHolder(0, BlockFilter.ALL, "", BlockCategory.ALL),
                    new CategoryGuiHolder(), new RecentGuiHolder(0),
                    new ItemGuiHolder(0, BlockFilter.ALL), new RecipeGuiHolder());
            for (InventoryHolder holder : holders) {
                when(current.getHolder(false)).thenReturn(holder);
                gui.closeOpenMenus();
            }
            when(current.getHolder(false)).thenReturn(mock(InventoryHolder.class));
            gui.closeOpenMenus();
            verify(player, times(holders.size())).closeInventory();
        }
    }

    private MockedConstruction<ItemStack> mockItems() {
        return mockConstruction(ItemStack.class, (item, context) ->
                when(item.getItemMeta()).thenReturn(mock(ItemMeta.class)));
    }

    private void stubInventories(MockedStatic<Bukkit> bukkit) {
        bukkit.when(Bukkit::getOnlinePlayers).thenReturn(List.of(player));
        bukkit.when(() -> Bukkit.createInventory(any(InventoryHolder.class), eq(54), any(Component.class)))
                .thenAnswer(call -> {
                    InventoryHolder holder = call.getArgument(0);
                    rendered.add(holder);
                    Inventory inventory = mock(Inventory.class);
                    when(inventory.getHolder(false)).thenReturn(holder);
                    when(inventory.getSize()).thenReturn(54);
                    return inventory;
                });
    }
}
