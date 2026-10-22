package dev.everyblock.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

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

    @ParameterizedTest
    @CsvSource({
            "SANDSTONE, STONE",
            "RED_SANDSTONE, STONE",
            "CUT_SANDSTONE_SLAB, STONE",
            "SMOOTH_RED_SANDSTONE_STAIRS, STONE",
            "CHISELED_SANDSTONE, STONE",
            "MUD_BRICKS, STONE",
            "MUD_BRICK_WALL, STONE",
            "MOSSY_COBBLESTONE, STONE",
            "MOSSY_COBBLESTONE_SLAB, STONE",
            "MOSSY_STONE_BRICKS, STONE",
            "MOSSY_STONE_BRICK_STAIRS, STONE",
            "TORCHFLOWER, PLANTS",
            "FLOWER_POT, FUNCTIONAL",
            "MOSS_BLOCK, PLANTS",
            "MOSS_CARPET, PLANTS",
            "PALE_MOSS_CARPET, PLANTS",
            "PACKED_MUD, TERRAIN",
            "MUD, TERRAIN",
            "SAND, TERRAIN",
            "RED_SAND, TERRAIN"
    })
    void keepsMasonryAndLookalikeNamesInTheirFamilies(String name, BlockCategory expected) {
        assertEquals(expected, BlockCategory.ofName(name));
    }

    @ParameterizedTest
    @CsvSource({
            "GRASS_BLOCK, TERRAIN",
            "BLUE_ICE, TERRAIN",
            "CALCITE, TERRAIN",
            "COAL_ORE, ORES",
            "RAW_GOLD_BLOCK, ORES",
            "AMETHYST_BLOCK, ORES",
            "OAK_LEAVES, PLANTS",
            "BIRCH_SAPLING, PLANTS",
            "SUNFLOWER, PLANTS",
            "SPRUCE_LOG, WOOD",
            "MANGROVE_PLANKS, WOOD",
            "STONE_BRICKS, STONE",
            "DEEPSLATE_TILES, STONE",
            "PRISMARINE_BRICKS, STONE",
            "WHITE_WOOL, COLOURED",
            "RED_BED, COLOURED",
            "TORCH, GLASS_LIGHT",
            "GLASS_PANE, GLASS_LIGHT",
            "SEA_LANTERN, GLASS_LIGHT",
            "OBSERVER, REDSTONE",
            "POWERED_RAIL, REDSTONE",
            "CHEST, FUNCTIONAL",
            "BLAST_FURNACE, FUNCTIONAL",
            "NETHER_BRICKS, NETHER",
            "SOUL_TORCH, NETHER",
            "END_STONE_BRICKS, END",
            "CHORUS_FLOWER, END"
    })
    void sanityTable(String name, BlockCategory expected) {
        assertEquals(expected, BlockCategory.ofName(name));
    }

    @ParameterizedTest
    @CsvSource({
            "POPPY, PLANTS",
            "FERN, PLANTS",
            "LARGE_FERN, PLANTS",
            "SHORT_GRASS, PLANTS",
            "TALL_GRASS, PLANTS",
            "DEAD_BUSH, PLANTS",
            "ROSE_BUSH, PLANTS",
            "ALLIUM, PLANTS",
            "AZURE_BLUET, PLANTS",
            "OXEYE_DAISY, PLANTS",
            "LILAC, PLANTS",
            "PEONY, PLANTS",
            "PINK_PETALS, PLANTS",
            "SEA_PICKLE, PLANTS",
            "GLOW_LICHEN, PLANTS",
            "MANGROVE_PROPAGULE, PLANTS",
            "GRASS_BLOCK, TERRAIN",
            "COPPER_TORCH, GLASS_LIGHT",
            "COPPER_LANTERN, GLASS_LIGHT",
            "WAXED_OXIDIZED_COPPER_BULB, GLASS_LIGHT",
            "COPPER_CHEST, FUNCTIONAL",
            "COPPER_BLOCK, ORES",
            "WEATHERED_CUT_COPPER_STAIRS, ORES"
    })
    void placesPlantsAndCopperFixturesInTheirFamilies(String name, BlockCategory expected) {
        assertEquals(expected, BlockCategory.ofName(name));
    }

    @Test
    void memoisedLookupMatchesClassification() {
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_")) {
                continue;
            }
            BlockCategory first = BlockCategory.of(material);
            assertEquals(BlockCategory.ofName(material.name()), first, material.name());
            assertSame(first, BlockCategory.of(material));
        }
    }
}
