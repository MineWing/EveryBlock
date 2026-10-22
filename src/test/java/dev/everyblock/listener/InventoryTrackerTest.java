package dev.everyblock.listener;

import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.OfflinePlayer;
import org.bukkit.Server;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitScheduler;
import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.message.MessageService;
import dev.everyblock.model.Contributor;
import dev.everyblock.model.Discovery;
import dev.everyblock.service.BlockCatalogue;
import dev.everyblock.service.ItemCatalogue;
import dev.everyblock.service.ProgressService;
import dev.everyblock.service.ItemProgressService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTrackerTest {
    @Test
    void challengePermissionsAreIndependent() {
        for (boolean blocksAllowed : List.of(false, true)) {
            for (boolean itemsAllowed : List.of(false, true)) {
                Fixture fixture = new Fixture();
                Player player = fixture.player("Finder", blocksAllowed, itemsAllowed, Material.STONE);

                fixture.tracker.scan(player);

                verify(fixture.progress, times(blocksAllowed ? 1 : 0)).discoverMany(eq(Set.of(Material.STONE)), any(), eq("Finder"));
                verify(fixture.itemProgress, times(itemsAllowed ? 1 : 0)).discoverMany(eq(Set.of(Material.STONE)), any(), eq("Finder"));
            }
        }
    }

    @Test
    void scanChecksPermissionsOncePerScanAndSkipsInventoryWithoutAny() {
        Fixture fixture = new Fixture();
        Player player = fixture.player("Finder", true, true, Material.STONE, Material.DIRT, Material.STONE);
        fixture.tracker.scan(player);
        verify(player, times(1)).hasPermission("everyblock.use");
        verify(player, times(1)).hasPermission("everyblock.items");

        Player outsider = fixture.player("Outsider", false, false, Material.STONE);
        fixture.tracker.scan(outsider);
        verify(outsider, never()).getInventory();

        when(fixture.settings.itemsEnabled()).thenReturn(false);
        Player itemsOnly = fixture.player("Items", false, true, Material.STONE);
        fixture.tracker.scan(itemsOnly);
        verify(itemsOnly, never()).getInventory();
        verify(itemsOnly, never()).hasPermission("everyblock.items");
    }

    @Test
    void removedBlockIsIgnoredForHoldersUntilTheyDropIt() {
        Fixture fixture = new Fixture();
        Player holder = fixture.player("Holder", true, false, Material.STONE);
        Player other = fixture.player("Other", true, false);
        UUID holderId = holder.getUniqueId();
        UUID otherId = other.getUniqueId();
        doReturn(List.of(holder, other)).when(fixture.server).getOnlinePlayers();

        fixture.tracker.suppressHolders(Material.STONE);
        assertTrue(fixture.tracker.suppressed(holder.getUniqueId(), Material.STONE));
        assertFalse(fixture.tracker.suppressed(other.getUniqueId(), Material.STONE));

        fixture.tracker.scan(holder);
        verify(fixture.progress, never()).discoverMany(anyCollection(), any(), anyString());

        fixture.hold(other, Material.STONE);
        fixture.tracker.scan(other);
        verify(fixture.progress).discoverMany(eq(Set.of(Material.STONE)), eq(otherId), eq("Other"));

        fixture.hold(holder);
        fixture.tracker.scan(holder);
        assertFalse(fixture.tracker.suppressed(holder.getUniqueId(), Material.STONE));
        fixture.hold(holder, Material.STONE);
        fixture.tracker.scan(holder);
        verify(fixture.progress).discoverMany(eq(Set.of(Material.STONE)), eq(holderId), eq("Holder"));
    }

    @Test
    void resetClearsSuppressions() {
        Fixture fixture = new Fixture();
        Player holder = fixture.player("Holder", true, false, Material.STONE);
        doReturn(List.of(holder)).when(fixture.server).getOnlinePlayers();
        fixture.tracker.suppressHolders(Material.STONE);
        fixture.tracker.clearSuppressions();
        assertFalse(fixture.tracker.suppressed(holder.getUniqueId(), Material.STONE));
        fixture.tracker.scan(holder);
        verify(fixture.progress).discoverMany(eq(Set.of(Material.STONE)), any(), eq("Holder"));
    }

    @Test
    void progressChangeToCompletionAnnouncesMilestoneAndPlaysFinaleOnce() {
        Fixture fixture = new Fixture();
        fixture.contributors(List.of(new Contributor(EveryBlockPlugin.CONSOLE_UUID, "CONSOLE", 10)));

        fixture.tracker.progressChanged(9, 10, 10, "Admin", Material.STONE);
        fixture.runScheduled();

        verify(fixture.messages).sendAll(eq("milestone"), argThat(map -> "100".equals(map.get("percent"))));
        Map<String, String> finalBlock = fixture.sent("complete-final-block");
        assertEquals("Admin", finalBlock.get("player"));
        assertEquals(InventoryTracker.display(Material.STONE), finalBlock.get("block"));
        assertEquals(List.of(20L, 70L, 120L, 190L), fixture.delays);
        verify(fixture.messages).sendAll(eq("complete-finale"), anyMap());

        fixture.tracker.progressChanged(10, 10, 10, "Admin", Material.STONE);
        fixture.tracker.progressChanged(4, 5, 10, "Admin", Material.DIRT);
        fixture.runScheduled();
        verify(fixture.messages, times(1)).sendAll(eq("complete-start"), anyMap());
        verify(fixture.messages).sendAll(eq("milestone"), argThat(map -> "50".equals(map.get("percent"))));
    }

    @Test
    void offlinePlaytimeIsReadOffTheServerThread() {
        Fixture fixture = new Fixture();
        UUID onlineId = UUID.randomUUID();
        UUID offlineId = UUID.randomUUID();
        fixture.contributors(List.of(new Contributor(onlineId, "Online", 6), new Contributor(offlineId, "Offline", 4)));
        Player online = mock(Player.class);
        when(online.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(1200);
        when(fixture.server.getPlayer(onlineId)).thenReturn(online);
        OfflinePlayer offline = mock(OfflinePlayer.class);
        when(offline.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(2400);
        when(fixture.server.getOfflinePlayer(offlineId)).thenReturn(offline);

        fixture.tracker.progressChanged(9, 10, 10, "Online", Material.STONE);

        verify(fixture.server, never()).getOfflinePlayer(any(UUID.class));
        assertEquals(1, fixture.async.size());
        assertTrue(fixture.later.isEmpty());
        fixture.async.removeFirst().run();
        assertEquals(1, fixture.sync.size());
        fixture.sync.removeFirst().run();
        fixture.runScheduled();

        assertEquals("3m 0s", fixture.sent("complete-stats").get("playtime"));
        assertEquals("2", fixture.sent("complete-stats").get("contributors"));
    }

    @Test
    void disabledPluginSkipsFinaleAfterLoadingOfflineStats() {
        Fixture fixture = new Fixture();
        UUID offlineId = UUID.randomUUID();
        fixture.contributors(List.of(new Contributor(offlineId, "Offline", 10)));
        when(fixture.server.getOfflinePlayer(offlineId)).thenReturn(mock(OfflinePlayer.class));
        fixture.tracker.progressChanged(9, 10, 10, "Offline", Material.STONE);
        when(fixture.plugin.isEnabled()).thenReturn(false);
        fixture.async.removeFirst().run();
        assertTrue(fixture.sync.isEmpty());
        verify(fixture.scheduler, never()).runTaskLater(any(), any(Runnable.class), anyLong());
    }

    @Test
    void reloadThatShrinksCatalogueToCollectedCompletesOnce() {
        Fixture fixture = new Fixture();
        fixture.contributors(List.of());
        when(fixture.progress.collectedCount()).thenReturn(9);
        when(fixture.progress.totalCount()).thenReturn(9);
        when(fixture.progress.latest()).thenReturn(Optional.of(
                new Discovery(Material.DIRT, UUID.randomUUID(), "Last finder", Instant.now())));

        fixture.tracker.catalogueChanged(true, true);
        fixture.runScheduled();
        verify(fixture.messages, never()).sendAll(eq("complete-start"), anyMap());

        fixture.tracker.catalogueChanged(false, true);
        fixture.runScheduled();
        Map<String, String> finalBlock = fixture.sent("complete-final-block");
        assertEquals("Last finder", finalBlock.get("player"));
        assertEquals(InventoryTracker.display(Material.DIRT), finalBlock.get("block"));
    }

    @Test
    void reloadThatCompletesItemsAnnouncesItemsComplete() {
        Fixture fixture = new Fixture();
        when(fixture.itemProgress.collectedCount()).thenReturn(3);
        when(fixture.itemProgress.totalCount()).thenReturn(3);
        when(fixture.itemProgress.recent()).thenReturn(List.of(
                new Discovery(Material.STICK, UUID.randomUUID(), "Crafter", Instant.now())));
        fixture.tracker.catalogueChanged(true, false);
        assertEquals("Crafter", fixture.sent("items-complete").get("player"));

        when(fixture.settings.itemsEnabled()).thenReturn(false);
        fixture.tracker.catalogueChanged(true, false);
        verify(fixture.messages, times(1)).sendAll(eq("items-complete"), anyMap());
    }

    @Test
    void stylesEpicAndLegendaryBlockNamesWithTheirGradients() {
        assertTrue(InventoryTracker.display(Material.CONDUIT)
                .contains("<gradient:#C084FC:#8B5CF6>Conduit</gradient>"));
        assertTrue(InventoryTracker.display(Material.DRAGON_EGG)
                .contains("<gradient:#FFF176:#FFD700:#FF8C00>Dragon Egg</gradient>"));
    }

    @Test
    void featuresEveryHighRarityPresentInABatch() {
        assertEquals(List.of(Material.DIAMOND_ORE, Material.CONDUIT, Material.DRAGON_EGG),
                InventoryTracker.featuredRarities(List.of(
                        Material.STONE,
                        Material.DRAGON_EGG,
                        Material.DIAMOND_ORE,
                        Material.CONDUIT,
                        Material.BEACON)));
    }

    private static final class Fixture {
        final EveryBlockPlugin plugin = mock(EveryBlockPlugin.class);
        final PluginSettings settings = mock(PluginSettings.class);
        final Server server = mock(Server.class);
        final BukkitScheduler scheduler = mock(BukkitScheduler.class);
        final MessageService messages = mock(MessageService.class);
        final ProgressService progress = mock(ProgressService.class);
        final ItemProgressService itemProgress = mock(ItemProgressService.class);
        final List<Runnable> later = new ArrayList<>();
        final List<Long> delays = new ArrayList<>();
        final List<Runnable> async = new ArrayList<>();
        final List<Runnable> sync = new ArrayList<>();
        final InventoryTracker tracker;

        Fixture() {
            when(plugin.settings()).thenReturn(settings);
            when(plugin.getServer()).thenReturn(server);
            when(plugin.isEnabled()).thenReturn(true);
            when(plugin.getLogger()).thenReturn(mock(Logger.class));
            when(plugin.messages()).thenReturn(messages);
            when(settings.participantMode()).thenReturn(PluginSettings.ParticipantMode.EVERYONE);
            when(settings.itemsEnabled()).thenReturn(true);
            when(settings.milestones()).thenReturn(List.of(25, 50, 100));
            BlockCatalogue blocks = mock(BlockCatalogue.class);
            ItemCatalogue items = mock(ItemCatalogue.class);
            when(plugin.catalogue()).thenReturn(blocks);
            when(plugin.itemCatalogue()).thenReturn(items);
            when(plugin.progress()).thenReturn(progress);
            when(plugin.itemProgress()).thenReturn(itemProgress);
            when(blocks.contains(Material.STONE)).thenReturn(true);
            when(items.contains(Material.STONE)).thenReturn(true);
            when(progress.discoverMany(anyCollection(), any(), anyString())).thenReturn(new CompletableFuture<>());
            when(itemProgress.discoverMany(anyCollection(), any(), anyString())).thenReturn(new CompletableFuture<>());
            when(server.getScheduler()).thenReturn(scheduler);
            when(scheduler.runTaskLater(eq(plugin), any(Runnable.class), anyLong())).thenAnswer(call -> {
                later.add(call.getArgument(1));
                delays.add(call.getArgument(2));
                return null;
            });
            when(scheduler.runTaskAsynchronously(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                async.add(call.getArgument(1));
                return null;
            });
            when(scheduler.runTask(eq(plugin), any(Runnable.class))).thenAnswer(call -> {
                sync.add(call.getArgument(1));
                return null;
            });
            tracker = new InventoryTracker(plugin);
        }

        Player player(String name, boolean blocks, boolean items, Material... held) {
            Player player = mock(Player.class);
            when(player.isOnline()).thenReturn(true);
            when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
            when(player.getUniqueId()).thenReturn(UUID.randomUUID());
            when(player.getName()).thenReturn(name);
            when(player.hasPermission("everyblock.use")).thenReturn(blocks);
            when(player.hasPermission("everyblock.items")).thenReturn(items);
            when(player.getInventory()).thenReturn(mock(PlayerInventory.class));
            hold(player, held);
            clearInvocations(player);
            return player;
        }

        void hold(Player player, Material... held) {
            ItemStack[] contents = new ItemStack[held.length + 1];
            for (int index = 0; index < held.length; index++) {
                ItemStack stack = mock(ItemStack.class);
                when(stack.getType()).thenReturn(held[index]);
                contents[index] = stack;
            }
            PlayerInventory inventory = player.getInventory();
            when(inventory.getContents()).thenReturn(contents);
        }

        void contributors(List<Contributor> contributors) {
            when(progress.contributors()).thenReturn(contributors);
        }

        void runScheduled() {
            while (!later.isEmpty()) {
                later.removeFirst().run();
            }
        }

        @SuppressWarnings("unchecked")
        Map<String, String> sent(String key) {
            ArgumentCaptor<Map<String, String>> captor = ArgumentCaptor.forClass(Map.class);
            verify(messages, atLeastOnce()).sendAll(eq(key), captor.capture());
            return captor.getValue();
        }
    }
}
