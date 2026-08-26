package dev.everyblock.listener;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InventoryTrackerTest {
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
