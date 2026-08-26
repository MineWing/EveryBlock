package dev.everyblock.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ItemCatalogueTest {
    @Test
    void rejectsCommandOnlyAndSpawnEggItems() {
        assertFalse(ItemCatalogue.survivalObtainable("DEBUG_STICK"));
        assertFalse(ItemCatalogue.survivalObtainable("KNOWLEDGE_BOOK"));
        assertFalse(ItemCatalogue.survivalObtainable("COMMAND_BLOCK_MINECART"));
        assertFalse(ItemCatalogue.survivalObtainable("CREEPER_SPAWN_EGG"));
        assertFalse(ItemCatalogue.survivalObtainable("BEDROCK"));
        assertFalse(ItemCatalogue.survivalObtainable("PLAYER_HEAD"));
    }

    @Test
    void keepsDifficultSurvivalItems() {
        assertTrue(ItemCatalogue.survivalObtainable("ELYTRA"));
        assertTrue(ItemCatalogue.survivalObtainable("NETHER_STAR"));
        assertTrue(ItemCatalogue.survivalObtainable("HEAVY_CORE"));
        assertTrue(ItemCatalogue.survivalObtainable("DRAGON_EGG"));
    }
}
