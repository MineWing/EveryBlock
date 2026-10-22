package dev.everyblock.command;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.gui.GuiService;
import dev.everyblock.listener.InventoryTracker;
import dev.everyblock.message.MessageService;
import dev.everyblock.service.BlockCatalogue;
import dev.everyblock.service.ProgressService;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlocksCommandTest {
    private EveryBlockPlugin plugin;
    private MessageService messages;
    private ProgressService progress;
    private BlockCatalogue catalogue;
    private InventoryTracker tracker;
    private Logger logger;
    private BlocksCommand command;
    private final Command bukkitCommand = mock(Command.class);

    @BeforeEach
    void setUp() {
        plugin = mock(EveryBlockPlugin.class);
        messages = mock(MessageService.class);
        progress = mock(ProgressService.class);
        catalogue = mock(BlockCatalogue.class);
        tracker = mock(InventoryTracker.class);
        logger = mock(Logger.class);
        when(plugin.messages()).thenReturn(messages);
        when(plugin.progress()).thenReturn(progress);
        when(plugin.catalogue()).thenReturn(catalogue);
        when(plugin.tracker()).thenReturn(tracker);
        when(plugin.gui()).thenReturn(mock(GuiService.class));
        when(plugin.getLogger()).thenReturn(logger);
        when(catalogue.match("stone")).thenReturn(Material.STONE);
        when(catalogue.blocks()).thenReturn(List.of(Material.STONE, Material.DIRT));
        when(progress.found(Material.DIRT)).thenReturn(true);
        command = new BlocksCommand(plugin);
    }

    private CommandSender sender(String name, boolean use, boolean admin) {
        CommandSender sender = mock(CommandSender.class);
        when(sender.getName()).thenReturn(name);
        when(sender.hasPermission("everyblock.use")).thenReturn(use);
        when(sender.hasPermission("everyblock.admin")).thenReturn(admin);
        return sender;
    }

    private void run(CommandSender sender, String... args) {
        command.onCommand(sender, bukkitCommand, "blocks", args);
    }

    @Test
    void adminSubcommandsNeedOnlyAdminPermission() {
        CommandSender admin = sender("Admin", false, true);
        when(plugin.reloadPlugin()).thenReturn(true);
        run(admin, "reload");
        verify(plugin).reloadPlugin();
        verify(messages).send(eq(admin), eq("reloaded"), anyMap());

        run(admin, "status");
        verify(messages).send(admin, "no-permission");
        verify(progress, never()).collectedCount();
    }

    @Test
    void playersWithoutAdminCannotRunAdminSubcommands() {
        CommandSender player = sender("Player", true, false);
        for (String subcommand : List.of("add", "remove", "reset", "reload")) {
            run(player, subcommand, "stone");
        }
        verify(messages, times(4)).send(player, "no-permission");
        verify(plugin, never()).reloadPlugin();
        verifyNoInteractions(tracker);
        verify(progress, never()).add(any(), any(), any());
        verify(progress, never()).remove(any());
        verify(progress, never()).reset();
    }

    @Test
    void addingTheFinalBlockRunsMilestoneAndCompletionAfterTheAddedMessage() {
        CommandSender admin = sender("Admin", false, true);
        when(progress.add(eq(Material.STONE), any(), eq("Admin"))).thenReturn(CompletableFuture.completedFuture(true));
        when(progress.collectedCount()).thenReturn(10);
        when(progress.totalCount()).thenReturn(10);
        run(admin, "add", "stone");
        var order = inOrder(messages, tracker);
        order.verify(messages).send(eq(admin), eq("added"), anyMap());
        order.verify(tracker).progressChanged(9, 10, 10, "Admin", Material.STONE);
    }

    @Test
    void addingAnAlreadyFoundBlockDoesNotReportProgress() {
        CommandSender admin = sender("Admin", false, true);
        when(progress.add(eq(Material.STONE), any(), any())).thenReturn(CompletableFuture.completedFuture(false));
        run(admin, "add", "stone");
        verify(messages).send(eq(admin), eq("already-found"), anyMap());
        verify(tracker, never()).progressChanged(anyInt(), anyInt(), anyInt(), any(), any());
    }

    @Test
    void removingABlockSuppressesCurrentHolders() {
        CommandSender admin = sender("Admin", false, true);
        when(progress.remove(Material.STONE)).thenReturn(CompletableFuture.completedFuture(true));
        run(admin, "remove", "stone");
        verify(tracker).suppressHolders(Material.STONE);

        when(progress.remove(Material.STONE)).thenReturn(CompletableFuture.completedFuture(false));
        run(admin, "remove", "stone");
        verify(tracker, times(1)).suppressHolders(Material.STONE);
    }

    @Test
    void resetClearsRemovalSuppressions() {
        CommandSender admin = sender("Admin", false, true);
        when(progress.reset()).thenReturn(CompletableFuture.completedFuture(null));
        run(admin, "reset", "confirm");
        verify(tracker).clearSuppressions();
        verify(messages).sendAll(eq("reset-complete"), anyMap());
    }

    @Test
    void databaseErrorsLogTheUnderlyingCause() {
        CommandSender admin = sender("Admin", false, true);
        when(progress.add(any(), any(), any())).thenReturn(CompletableFuture.failedFuture(
                new CompletionException(new SQLException("disk full"))));
        run(admin, "add", "stone");
        verify(logger).severe("Could not update block progress: disk full");
        verify(messages).send(admin, "database-error");
    }

    @Test
    void adminTabCompletionsAreHiddenFromNonAdmins() {
        CommandSender player = sender("Player", true, false);
        assertEquals(List.of(), command.onTabComplete(player, bukkitCommand, "blocks", new String[]{"add", ""}));
        assertEquals(List.of(), command.onTabComplete(player, bukkitCommand, "blocks", new String[]{"remove", ""}));
        assertEquals(List.of(), command.onTabComplete(player, bukkitCommand, "blocks", new String[]{"reset", ""}));
        assertEquals(List.of("status", "search"),
                command.onTabComplete(player, bukkitCommand, "blocks", new String[]{"S"}));
        assertEquals(List.of("found"),
                command.onTabComplete(player, bukkitCommand, "blocks", new String[]{"gui", "f"}));

        CommandSender admin = sender("Admin", false, true);
        assertEquals(List.of("remove", "reset", "reload"),
                command.onTabComplete(admin, bukkitCommand, "blocks", new String[]{"re"}));
        assertEquals(List.of("stone"), command.onTabComplete(admin, bukkitCommand, "blocks", new String[]{"add", ""}));
        assertEquals(List.of("dirt"), command.onTabComplete(admin, bukkitCommand, "blocks", new String[]{"remove", ""}));
        assertEquals(List.of("confirm"), command.onTabComplete(admin, bukkitCommand, "blocks", new String[]{"reset", "C"}));
    }
}
