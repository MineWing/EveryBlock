package dev.everyblock.command;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.gui.ItemGuiService;
import dev.everyblock.message.MessageService;
import dev.everyblock.service.ItemProgressService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ItemsCommandTest {
    private EveryBlockPlugin plugin;
    private PluginSettings settings;
    private MessageService messages;
    private ItemProgressService itemProgress;
    private ItemsCommand command;
    private final Command bukkitCommand = mock(Command.class);

    @BeforeEach
    void setUp() {
        plugin = mock(EveryBlockPlugin.class);
        settings = mock(PluginSettings.class);
        messages = mock(MessageService.class);
        itemProgress = mock(ItemProgressService.class);
        when(plugin.settings()).thenReturn(settings);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.itemProgress()).thenReturn(itemProgress);
        when(plugin.itemGui()).thenReturn(mock(ItemGuiService.class));
        when(plugin.getLogger()).thenReturn(mock(Logger.class));
        when(itemProgress.reset()).thenReturn(CompletableFuture.completedFuture(null));
        command = new ItemsCommand(plugin);
    }

    private CommandSender sender(boolean items, boolean admin) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn("Sender");
        when(sender.hasPermission("everyblock.items")).thenReturn(items);
        when(sender.hasPermission("everyblock.admin")).thenReturn(admin);
        return sender;
    }

    @Test
    void resetNeedsOnlyAdminAndWorksWhileItemsAreDisabled() {
        when(settings.itemsEnabled()).thenReturn(false);
        CommandSender admin = sender(false, true);
        command.onCommand(admin, bukkitCommand, "items", new String[]{"reset"});
        verify(messages).send(admin, "items-reset-warning");
        command.onCommand(admin, bukkitCommand, "items", new String[]{"reset", "confirm"});
        verify(itemProgress).reset();
        verify(messages).sendAll(eq("items-reset"), anyMap());
    }

    @Test
    void resetIsRefusedWithoutAdmin() {
        when(settings.itemsEnabled()).thenReturn(true);
        CommandSender player = sender(true, false);
        command.onCommand(player, bukkitCommand, "items", new String[]{"reset", "confirm"});
        verify(messages).send(player, "no-permission");
        verify(itemProgress, never()).reset();
    }

    @Test
    void otherSubcommandsKeepItemsPermissionAndEnabledGating() {
        CommandSender admin = sender(false, true);
        when(settings.itemsEnabled()).thenReturn(true);
        command.onCommand(admin, bukkitCommand, "items", new String[]{"status"});
        verify(messages).send(admin, "no-permission");

        CommandSender player = sender(true, false);
        when(settings.itemsEnabled()).thenReturn(false);
        command.onCommand(player, bukkitCommand, "items", new String[]{"status"});
        verify(messages).send(player, "items-disabled");
        verify(itemProgress, never()).collectedCount();
    }

    @Test
    void tabCompletionFiltersByTypedPrefix() {
        CommandSender player = sender(true, false);
        CommandSender admin = sender(false, true);
        assertEquals(List.of("status"), command.onTabComplete(player, bukkitCommand, "items", new String[]{""}));
        assertEquals(List.of(), command.onTabComplete(player, bukkitCommand, "items", new String[]{"r"}));
        assertEquals(List.of("reset"), command.onTabComplete(admin, bukkitCommand, "items", new String[]{"R"}));
        assertEquals(List.of("status"), command.onTabComplete(admin, bukkitCommand, "items", new String[]{"St"}));
        assertEquals(List.of("confirm"), command.onTabComplete(admin, bukkitCommand, "items", new String[]{"reset", "CON"}));
        assertEquals(List.of(), command.onTabComplete(admin, bukkitCommand, "items", new String[]{"reset", "x"}));
        assertEquals(List.of(), command.onTabComplete(player, bukkitCommand, "items", new String[]{"reset", ""}));
    }
}
