package dev.everyblock.service;

import org.junit.jupiter.api.Test;
import org.bukkit.Material;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockCatalogueTest {
    @Test
    void appliesExactAndFamilyExclusions() {
        Set<String> exact = Set.of("BEDROCK", "BARRIER");
        List<String> prefixes = List.of("INFESTED_");

        assertTrue(BlockCatalogue.excluded("BEDROCK", exact, prefixes));
        assertTrue(BlockCatalogue.excluded("INFESTED_STONE", exact, prefixes));
        assertFalse(BlockCatalogue.excluded("STONE", exact, prefixes));
    }

    @Test
    void rejectsAirBeforeBuildingGuiItems() {
        assertFalse(BlockCatalogue.nonAir(Material.AIR));
        assertFalse(BlockCatalogue.nonAir(Material.CAVE_AIR));
        assertFalse(BlockCatalogue.nonAir(Material.VOID_AIR));
        assertTrue(BlockCatalogue.nonAir(Material.STONE));
    }

    @Test
    void permanentlyRejectsCommandOnlyAndUnobtainableSurvivalBlocks() {
        for (String material : List.of(
                "BEDROCK", "BARRIER", "BUDDING_AMETHYST", "COMMAND_BLOCK",
                "END_PORTAL_FRAME", "FROGSPAWN", "PETRIFIED_OAK_SLAB",
                "PLAYER_HEAD", "REINFORCED_DEEPSLATE", "SPAWNER", "SUSPICIOUS_SAND",
                "TEST_BLOCK", "TEST_INSTANCE_BLOCK", "TRIAL_SPAWNER", "VAULT", "INFESTED_STONE")) {
            assertFalse(BlockCatalogue.survivalObtainable(material), material);
        }
        for (String material : List.of(
                "DRAGON_EGG", "DEEPSLATE_EMERALD_ORE", "SCULK_SHRIEKER",
                "GILDED_BLACKSTONE", "DRAGON_HEAD")) {
            assertTrue(BlockCatalogue.survivalObtainable(material), material);
        }
    }
}
