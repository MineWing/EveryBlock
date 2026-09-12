package dev.everyblock;

import dev.everyblock.config.ConfigService;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.gui.GuiService;
import dev.everyblock.gui.ItemGuiService;
import dev.everyblock.hook.EveryBlockExpansion;
import dev.everyblock.listener.InventoryTracker;
import dev.everyblock.message.MessageService;
import dev.everyblock.service.BlockCatalogue;
import dev.everyblock.service.ItemCatalogue;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.PluginManager;
import org.bukkit.scheduler.BukkitScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.UUID;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EveryBlockPluginTest {
    private EveryBlockPlugin plugin;
    private ConfigService config;
    private MessageService messages;
    private GuiService gui;
    private ItemGuiService itemGui;
    private InventoryTracker tracker;
    private BlockCatalogue blocks;
    private ItemCatalogue items;

    @BeforeEach
    void setUp() throws Exception {
        plugin = mock(EveryBlockPlugin.class, CALLS_REAL_METHODS);
        doReturn(mock(Logger.class)).when(plugin).getLogger();
        config = mock(ConfigService.class);
        messages = new MessageService(null);
        YamlConfiguration original = new YamlConfiguration();
        original.set("tag", "original");
        messages.apply(original);
        messages = spy(messages);
        gui = mock(GuiService.class);
        itemGui = mock(ItemGuiService.class);
        tracker = mock(InventoryTracker.class);
        blocks = mock(BlockCatalogue.class);
        items = mock(ItemCatalogue.class);
        field("configService", config);
        field("messages", messages);
        field("gui", gui);
        field("itemGui", itemGui);
        field("tracker", tracker);
        field("catalogue", blocks);
        field("itemCatalogue", items);
        when(config.settings()).thenReturn(settings(Path.of("/tmp/original.db")));
    }

    @Test
    void failedMessagesPreparationDoesNotApplyConfigOrRestartTracking() {
        when(config.prepare()).thenReturn(settings(Path.of("/tmp/original.db")));
        doThrow(new IllegalArgumentException("bad messages YAML")).when(messages).prepare();
        assertFalse(plugin.reloadPlugin());
        verify(config, never()).apply(any());
        assertEquals("original", messages.string("tag", java.util.Map.of(), null));
        verifyNoInteractions(blocks, items, tracker, gui, itemGui);
    }

    @Test
    void successfulReloadRetainsOpenDatabaseAndRefreshesBothChallenges() {
        when(config.prepare()).thenReturn(settings(Path.of("/tmp/changed.db")));
        doReturn(new YamlConfiguration()).when(messages).prepare();
        assertTrue(plugin.reloadPlugin());
        var applied = org.mockito.ArgumentCaptor.forClass(PluginSettings.class);
        verify(config).apply(applied.capture());
        assertEquals(Path.of("/tmp/original.db"), applied.getValue().databasePath());
        verify(tracker).start();
        verify(gui).refreshOpenMenus();
        verify(itemGui).refreshOpenMenus();
    }

    @Test
    void disableClosesMenusEffectsExpansionAndDatabase() throws Exception {
        ProgressDatabase database = mock(ProgressDatabase.class);
        EveryBlockExpansion expansion = mock(EveryBlockExpansion.class);
        field("database", database);
        field("expansion", expansion);
        plugin.onDisable();
        var order = inOrder(tracker, gui, messages, expansion, database);
        order.verify(tracker).stop();
        order.verify(gui).closeOpenMenus();
        order.verify(messages).shutdown();
        order.verify(expansion).unregister();
        order.verify(database).close();
    }

    @Test
    void reenableAcceptsDatabaseCompletionAndUpdatesProgressOnServerTask() throws Exception {
        Server server = mock(Server.class);
        PluginManager manager = mock(PluginManager.class);
        BukkitScheduler scheduler = mock(BukkitScheduler.class);
        doReturn(server).when(plugin).getServer();
        doReturn(true).when(plugin).isEnabled();
        doReturn(mock(PluginCommand.class)).when(plugin).getCommand(anyString());
        when(server.getPluginManager()).thenReturn(manager);
        when(server.getScheduler()).thenReturn(scheduler);
        ArrayDeque<Runnable> serverTasks = new ArrayDeque<>();
        when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
            serverTasks.add(call.getArgument(1));
            return null;
        });
        AtomicReference<Executor> completionExecutor = new AtomicReference<>();
        try (var configs = mockConstruction(ConfigService.class, (instance, context) -> {
                 when(instance.prepare()).thenReturn(settings(Path.of("/tmp/lifecycle.db")));
                 when(instance.settings()).thenReturn(settings(Path.of("/tmp/lifecycle.db")));
             });
             var messageServices = mockConstruction(MessageService.class, (instance, context) -> {
                 when(instance.prepare()).thenReturn(new YamlConfiguration());
                 when(instance.string(anyString(), anyMap(), anyString())).thenAnswer(call -> call.getArgument(2));
             });
             var blockCatalogues = mockConstruction(BlockCatalogue.class, (instance, context) -> {
                 when(instance.contains(Material.STONE)).thenReturn(true);
                 when(instance.blocks()).thenReturn(List.of(Material.STONE));
             });
             var itemCatalogues = mockConstruction(ItemCatalogue.class);
             var databases = mockConstruction(ProgressDatabase.class, (instance, context) -> {
                 when(instance.load()).thenReturn(Map.of());
                 when(instance.loadItems()).thenReturn(Map.of());
                 doAnswer(call -> {
                     completionExecutor.set(call.getArgument(0));
                     return null;
                 }).when(instance).startWorker(any(Executor.class));
                 when(instance.submit(any())).thenAnswer(call -> {
                     CompletableFuture<Boolean> persisted = new CompletableFuture<>();
                     completionExecutor.get().execute(() -> persisted.complete(true));
                     return persisted;
                 });
             });
             var guis = mockConstruction(GuiService.class);
             var itemGuis = mockConstruction(ItemGuiService.class);
             var trackers = mockConstruction(InventoryTracker.class)) {
            plugin.onEnable();
            assertEquals(1, databases.constructed().size());
            CompletableFuture<Boolean> first = plugin.progress().add(Material.STONE, UUID.randomUUID(), "First");
            assertFalse(first.isDone());
            assertEquals(1, serverTasks.size());
            serverTasks.remove().run();
            assertTrue(first.join());
            assertTrue(plugin.progress().found(Material.STONE));

            plugin.onDisable();
            verify(databases.constructed().getFirst()).close();
            plugin.onEnable();
            assertEquals(2, databases.constructed().size());
            assertFalse(plugin.progress().found(Material.STONE));
            CompletableFuture<Boolean> second = plugin.progress().add(Material.STONE, UUID.randomUUID(), "Second");
            assertFalse(second.isDone());
            assertFalse(plugin.progress().found(Material.STONE));
            assertEquals(1, serverTasks.size(), "Re-enabled plugin must schedule database completions");
            serverTasks.remove().run();
            assertTrue(second.join());
            assertEquals("Second", plugin.progress().discovery(Material.STONE).orElseThrow().playerName());
            verify(manager, never()).disablePlugin(plugin);
            plugin.onDisable();
        }
    }

    private static PluginSettings settings(Path database) {
        return new PluginSettings(PluginSettings.ParticipantMode.ALLOWLIST, Set.of(), 10, false,
                Set.of(), List.of(), Set.of(), false, Set.of(), List.of(), Set.of(), database,
                "Blocks", "dd MMM yyyy, HH:mm", List.of(10, 50));
    }

    private void field(String name, Object value) throws Exception {
        var field = EveryBlockPlugin.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(plugin, value);
    }
}
