package dev.everyblock.gui;

import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockRarityTest {
    @Test
    void ratesBlocksBySurvivalAcquisitionDifficulty() {
        assertEquals(BlockRarity.COMMON, BlockRarity.ofName("COBBLESTONE"));
        assertEquals(BlockRarity.UNCOMMON, BlockRarity.ofName("OXIDIZED_COPPER"));
        assertEquals(BlockRarity.RARE, BlockRarity.ofName("BRAIN_CORAL_BLOCK"));
        assertEquals(BlockRarity.RARE, BlockRarity.ofName("ANCIENT_DEBRIS"));
        assertEquals(BlockRarity.UNCOMMON, BlockRarity.ofName("CREEPER_HEAD"));
        assertEquals(BlockRarity.UNCOMMON, BlockRarity.ofName("WITHER_SKELETON_SKULL"));
        assertEquals(BlockRarity.RARE, BlockRarity.ofName("DRAGON_HEAD"));
        assertEquals(BlockRarity.EPIC, BlockRarity.ofName("CONDUIT"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("DEEPSLATE_EMERALD_ORE"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("DRAGON_EGG"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("HEAVY_CORE"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("NETHERITE_BLOCK"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("SNIFFER_EGG"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("WITHER_ROSE"));
    }

    @Test
    void deepslateOresAreAtLeastAsRareAsTheirStoneVariant() {
        assertEquals(BlockRarity.RARE, BlockRarity.ofName("DEEPSLATE_DIAMOND_ORE"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("DEEPSLATE_EMERALD_ORE"));
        assertEquals(BlockRarity.LEGENDARY, BlockRarity.ofName("DEEPSLATE_COAL_ORE"));
        assertEquals(BlockRarity.UNCOMMON, BlockRarity.ofName("DEEPSLATE_IRON_ORE"));
        assertEquals(BlockRarity.UNCOMMON, BlockRarity.ofName("DEEPSLATE_COPPER_ORE"));
        for (String ore : List.of("COAL", "IRON", "COPPER", "GOLD", "REDSTONE", "LAPIS", "DIAMOND", "EMERALD")) {
            BlockRarity stone = BlockRarity.ofName(ore + "_ORE");
            BlockRarity deepslate = BlockRarity.ofName("DEEPSLATE_" + ore + "_ORE");
            assertTrue(deepslate.compareTo(stone) >= 0, ore);
        }
    }

    @Test
    void memoisedLookupMatchesClassification() {
        for (Material material : Material.values()) {
            if (material.name().startsWith("LEGACY_")) {
                continue;
            }
            BlockRarity first = BlockRarity.of(material);
            assertEquals(BlockRarity.ofName(material.name()), first, material.name());
            assertSame(first, BlockRarity.of(material));
        }
    }

    @Test
    void enumOrderMatchesGuiSortOrder() {
        assertTrue(BlockRarity.COMMON.compareTo(BlockRarity.UNCOMMON) < 0);
        assertTrue(BlockRarity.UNCOMMON.compareTo(BlockRarity.RARE) < 0);
        assertTrue(BlockRarity.RARE.compareTo(BlockRarity.EPIC) < 0);
        assertTrue(BlockRarity.EPIC.compareTo(BlockRarity.LEGENDARY) < 0);
    }

    @Test
    void usesConfiguredGradientsForTopRarities() {
        assertEquals("<gradient:#C084FC:#8B5CF6>ᴇᴘɪᴄ</gradient>",
                BlockRarity.EPIC.miniMessage());
        assertEquals("<gradient:#FFF176:#FFD700:#FF8C00>ʟᴇɢᴇɴᴅᴀʀʏ</gradient>",
                BlockRarity.LEGENDARY.miniMessage());
    }
}
