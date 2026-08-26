package dev.everyblock.gui;

import dev.everyblock.message.MessageService;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public enum BlockCategory {
    ALL(Material.NETHER_STAR, "All Blocks", "Every block in the challenge"),
    TERRAIN(Material.GRASS_BLOCK, "Terrain", "Dirt, sand, ice and natural ground"),
    ORES(Material.DIAMOND_ORE, "Ores & Minerals", "Ores, raw blocks and mineral blocks"),
    PLANTS(Material.FLOWERING_AZALEA, "Plants & Ocean", "Plants, leaves, coral and aquatic blocks"),
    WOOD(Material.OAK_LOG, "Wood Families", "Logs, planks and wooden building pieces"),
    STONE(Material.STONE_BRICKS, "Stone & Masonry", "Stone, bricks and masonry families"),
    COLOURED(Material.MAGENTA_GLAZED_TERRACOTTA, "Colours & Fabrics", "Wool, concrete, terracotta and beds"),
    GLASS_LIGHT(Material.SEA_LANTERN, "Glass & Lighting", "Glass, lamps, lanterns and light sources"),
    REDSTONE(Material.REDSTONE, "Redstone", "Components, rails and mechanisms"),
    FUNCTIONAL(Material.CRAFTING_TABLE, "Utility & Storage", "Workstations, storage and useful blocks"),
    NETHER(Material.NETHERRACK, "The Nether", "Blocks originating in the Nether"),
    END(Material.END_STONE, "The End", "Blocks originating in the End"),
    MISC(Material.DRAGON_EGG, "Special & Misc", "Unique blocks that belong nowhere else");

    private static final List<String> WOOD_PREFIXES = List.of(
            "OAK_", "SPRUCE_", "BIRCH_", "JUNGLE_", "ACACIA_", "DARK_OAK_",
            "MANGROVE_", "CHERRY_", "PALE_OAK_", "BAMBOO_"
    );
    private static final List<String> NETHER_WORDS = List.of(
            "NETHER", "NETHERRACK", "CRIMSON", "WARPED", "SOUL_", "BASALT",
            "BLACKSTONE", "MAGMA", "GLOWSTONE", "SHROOMLIGHT", "ANCIENT_DEBRIS",
            "RESPAWN_ANCHOR", "LODESTONE"
    );
    private static final List<String> END_WORDS = List.of(
            "END_STONE", "PURPUR", "CHORUS", "DRAGON_EGG", "END_ROD"
    );
    private static final List<String> REDSTONE_WORDS = List.of(
            "REDSTONE", "PISTON", "OBSERVER", "REPEATER", "COMPARATOR", "DISPENSER",
            "DROPPER", "HOPPER", "RAIL", "PRESSURE_PLATE", "BUTTON", "LEVER", "TARGET",
            "TRIPWIRE", "DAYLIGHT_DETECTOR", "SCULK_SENSOR", "CALIBRATED_SCULK_SENSOR",
            "CRAFTER", "LIGHTNING_ROD", "TNT", "NOTE_BLOCK"
    );
    private static final List<String> ORE_WORDS = List.of(
            "_ORE", "RAW_", "COAL_BLOCK", "IRON_BLOCK", "GOLD_BLOCK", "COPPER",
            "DIAMOND_BLOCK", "EMERALD_BLOCK", "LAPIS_BLOCK", "AMETHYST", "QUARTZ_BLOCK"
    );
    private static final List<String> COLOURED_WORDS = List.of(
            "_WOOL", "_CARPET", "_CONCRETE", "_TERRACOTTA", "_STAINED_GLASS",
            "_GLAZED_TERRACOTTA", "_BANNER", "_BED", "_SHULKER_BOX", "_CANDLE"
    );
    private static final List<String> GLASS_LIGHT_WORDS = List.of(
            "GLASS", "LANTERN", "TORCH", "LAMP", "LIGHT", "CANDLE", "BEACON",
            "SEA_LANTERN", "JACK_O_LANTERN", "OCHRE_FROGLIGHT", "VERDANT_FROGLIGHT",
            "PEARLESCENT_FROGLIGHT"
    );
    private static final List<String> PLANT_WORDS = List.of(
            "LEAVES", "SAPLING", "FLOWER", "TULIP", "ORCHID", "DANDELION", "AZALEA",
            "VINE", "CACTUS", "SUGAR_CANE", "BAMBOO", "MUSHROOM", "FUNGUS", "ROOTS",
            "CORAL", "KELP", "SEAGRASS", "LILY", "DRIPLEAF", "MOSS", "SPONGE",
            "PUMPKIN", "MELON", "HAY_BLOCK", "NETHER_WART", "WHEAT", "CAKE",
            "COCOA", "PITCHER", "TORCHFLOWER", "SPORE_BLOSSOM", "HANGING_ROOTS"
    );
    private static final List<String> TERRAIN_WORDS = List.of(
            "DIRT", "GRASS_BLOCK", "PODZOL", "MYCELIUM", "SAND", "GRAVEL", "CLAY",
            "MUD", "SNOW", "ICE", "PACKED_ICE", "BLUE_ICE", "SCULK", "DRIPSTONE",
            "CALCITE", "ROOTED_DIRT"
    );
    private static final List<String> STONE_WORDS = List.of(
            "STONE", "COBBLE", "BRICK", "DEEPSLATE", "TUFF", "GRANITE", "DIORITE",
            "ANDESITE", "SANDSTONE", "PRISMARINE", "PURPUR", "QUARTZ", "SLATE",
            "BRICKS", "CHISELED", "POLISHED"
    );
    private static final List<String> FUNCTIONAL_WORDS = List.of(
            "CRAFTING_TABLE", "FURNACE", "SMOKER", "BLAST_FURNACE", "CHEST", "BARREL",
            "SHULKER_BOX", "ANVIL", "ENCHANTING_TABLE", "BREWING_STAND", "CAULDRON",
            "COMPOSTER", "LOOM", "CARTOGRAPHY_TABLE", "FLETCHING_TABLE", "SMITHING_TABLE",
            "STONECUTTER", "GRINDSTONE", "LECTERN", "BOOKSHELF", "JUKEBOX", "BEEHIVE",
            "BEE_NEST", "CAMPFIRE", "BELL", "LADDER", "SCAFFOLDING", "CONDUIT",
            "DECORATED_POT", "FLOWER_POT", "ENDER_CHEST"
    );

    private final Material icon;
    private final String defaultName;
    private final String defaultDescription;
    private static volatile Map<BlockCategory, String> names = Map.of();
    private static volatile Map<BlockCategory, String> descriptions = Map.of();

    BlockCategory(Material icon, String defaultName, String defaultDescription) {
        this.icon = icon;
        this.defaultName = defaultName;
        this.defaultDescription = defaultDescription;
    }

    /** Pulls display names and descriptions from messages.yml (gui.categories.*). */
    public static void configure(MessageService messages) {
        Map<BlockCategory, String> nameMap = new EnumMap<>(BlockCategory.class);
        Map<BlockCategory, String> descriptionMap = new EnumMap<>(BlockCategory.class);
        for (BlockCategory category : values()) {
            nameMap.put(category, messages.string("gui.categories." + category.name() + ".name",
                    Map.of(), category.defaultName));
            descriptionMap.put(category, messages.string("gui.categories." + category.name() + ".description",
                    Map.of(), category.defaultDescription));
        }
        names = Map.copyOf(nameMap);
        descriptions = Map.copyOf(descriptionMap);
    }

    public Material icon() {
        return icon;
    }

    public String displayName() {
        return names.getOrDefault(this, defaultName);
    }

    public String description() {
        return descriptions.getOrDefault(this, defaultDescription);
    }

    public static BlockCategory of(Material material) {
        return ofName(material.name());
    }

    static BlockCategory ofName(String name) {
        if (contains(name, NETHER_WORDS)) {
            return NETHER;
        }
        if (contains(name, END_WORDS)) {
            return END;
        }
        if (WOOD_PREFIXES.stream().anyMatch(name::startsWith)
                || name.endsWith("_LOG") || name.endsWith("_WOOD") || name.endsWith("_PLANKS")) {
            return WOOD;
        }
        if (contains(name, ORE_WORDS)) {
            return ORES;
        }
        if (contains(name, REDSTONE_WORDS)) {
            return REDSTONE;
        }
        if (contains(name, COLOURED_WORDS)) {
            return COLOURED;
        }
        if (contains(name, GLASS_LIGHT_WORDS)) {
            return GLASS_LIGHT;
        }
        if (contains(name, PLANT_WORDS)) {
            return PLANTS;
        }
        if (contains(name, TERRAIN_WORDS)) {
            return TERRAIN;
        }
        if (contains(name, FUNCTIONAL_WORDS)) {
            return FUNCTIONAL;
        }
        if (contains(name, STONE_WORDS)) {
            return STONE;
        }
        return MISC;
    }

    private static boolean contains(String name, List<String> needles) {
        return needles.stream().anyMatch(name::contains);
    }
}
