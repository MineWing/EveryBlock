package dev.everyblock.listener;

import org.bukkit.Material;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.config.PluginSettings;
import dev.everyblock.service.BlockCatalogue;
import dev.everyblock.service.ItemCatalogue;
import dev.everyblock.service.ProgressService;
import dev.everyblock.service.ItemProgressService;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTrackerTest {
    @Test
    void challengePermissionsAreIndependent() {
        for (boolean blocksAllowed : List.of(false, true)) {
            for (boolean itemsAllowed : List.of(false, true)) {
                EveryBlockPlugin plugin = mock(EveryBlockPlugin.class);
                PluginSettings settings = mock(PluginSettings.class);
                when(plugin.settings()).thenReturn(settings);
                when(settings.participantMode()).thenReturn(PluginSettings.ParticipantMode.EVERYONE);
                when(settings.itemsEnabled()).thenReturn(true);
                BlockCatalogue blocks = mock(BlockCatalogue.class);
                ItemCatalogue items = mock(ItemCatalogue.class);
                ProgressService progress = mock(ProgressService.class);
                ItemProgressService itemProgress = mock(ItemProgressService.class);
                when(plugin.catalogue()).thenReturn(blocks);
                when(plugin.itemCatalogue()).thenReturn(items);
                when(plugin.progress()).thenReturn(progress);
                when(plugin.itemProgress()).thenReturn(itemProgress);
                when(blocks.contains(Material.STONE)).thenReturn(true);
                when(items.contains(Material.STONE)).thenReturn(true);
                when(progress.discoverMany(anyCollection(), any(), anyString())).thenReturn(new CompletableFuture<>());
                when(itemProgress.discoverMany(anyCollection(), any(), anyString())).thenReturn(new CompletableFuture<>());
                Player player = mock(Player.class);
                when(player.isOnline()).thenReturn(true);
                when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
                when(player.getUniqueId()).thenReturn(UUID.randomUUID());
                when(player.getName()).thenReturn("Finder");
                when(player.hasPermission("everyblock.use")).thenReturn(blocksAllowed);
                when(player.hasPermission("everyblock.items")).thenReturn(itemsAllowed);
                PlayerInventory inventory = mock(PlayerInventory.class);
                ItemStack stack = mock(ItemStack.class);
                when(stack.getType()).thenReturn(Material.STONE);
                when(player.getInventory()).thenReturn(inventory);
                when(inventory.getContents()).thenReturn(new ItemStack[]{stack});

                new InventoryTracker(plugin).scan(player);

                verify(progress, times(blocksAllowed ? 1 : 0)).discoverMany(eq(Set.of(Material.STONE)), any(), eq("Finder"));
                verify(itemProgress, times(itemsAllowed ? 1 : 0)).discoverMany(eq(Set.of(Material.STONE)), any(), eq("Finder"));
            }
        }
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
}
