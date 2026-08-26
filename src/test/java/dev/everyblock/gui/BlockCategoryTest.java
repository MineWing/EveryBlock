package dev.everyblock.gui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BlockCategoryTest {
    @Test
    void assignsRepresentativeBlocksToUsefulCategories() {
        assertEquals(BlockCategory.WOOD, BlockCategory.ofName("CHERRY_STAIRS"));
        assertEquals(BlockCategory.ORES, BlockCategory.ofName("DEEPSLATE_DIAMOND_ORE"));
        assertEquals(BlockCategory.REDSTONE, BlockCategory.ofName("STICKY_PISTON"));
        assertEquals(BlockCategory.COLOURED, BlockCategory.ofName("LIGHT_BLUE_CONCRETE"));
        assertEquals(BlockCategory.NETHER, BlockCategory.ofName("CRIMSON_NYLIUM"));
        assertEquals(BlockCategory.END, BlockCategory.ofName("PURPUR_PILLAR"));
        assertEquals(BlockCategory.PLANTS, BlockCategory.ofName("BRAIN_CORAL_BLOCK"));
        assertEquals(BlockCategory.FUNCTIONAL, BlockCategory.ofName("STONECUTTER"));
        assertEquals(BlockCategory.STONE, BlockCategory.ofName("POLISHED_DIORITE_STAIRS"));
        assertEquals(BlockCategory.MISC, BlockCategory.ofName("SLIME_BLOCK"));
    }
}
