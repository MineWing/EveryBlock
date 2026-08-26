package dev.everyblock.gui;

import dev.everyblock.message.MessageService;
import org.bukkit.Material;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public enum BlockRarity {
    COMMON("ᴄᴏᴍᴍᴏɴ", "white"),
    UNCOMMON("ᴜɴᴄᴏᴍᴍᴏɴ", "green"),
    RARE("ʀᴀʀᴇ", "aqua"),
    EPIC("ᴇᴘɪᴄ", "gradient:#C084FC:#8B5CF6"),
    LEGENDARY("ʟᴇɢᴇɴᴅᴀʀʏ", "gradient:#FFF176:#FFD700:#FF8C00");

    private static final Set<String> LEGENDARY_BLOCKS = Set.of(
            "BEACON",
            "DEEPSLATE_COAL_ORE",
            "DEEPSLATE_EMERALD_ORE",
            "DRAGON_EGG",
            "HEAVY_CORE",
            "NETHERITE_BLOCK",
            "SNIFFER_EGG",
            "WITHER_ROSE"
    );

    private static final Set<String> EPIC_BLOCKS = Set.of(
            "CONDUIT",
            "GILDED_BLACKSTONE",
            "SCULK_SHRIEKER"
    );
    private static final Set<String> RARE_BLOCKS = Set.of(
            "ANCIENT_DEBRIS",
            "BEE_NEST",
            "BLUE_ICE",
            "CRYING_OBSIDIAN",
            "DIAMOND_ORE",
            "DRAGON_HEAD",
            "EMERALD_ORE",
            "SCULK_CATALYST",
            "SPONGE",
            "TURTLE_EGG",
            "WET_SPONGE"
    );
    private static final Set<String> UNCOMMON_BLOCKS = Set.of(
            "CREEPER_HEAD",
            "PIGLIN_HEAD",
            "SKELETON_SKULL",
            "WITHER_SKELETON_SKULL",
            "ZOMBIE_HEAD"
    );

    private final String defaultLabel;
    private final String defaultColor;
    private static volatile Map<BlockRarity, String> labels = Map.of();
    private static volatile Map<BlockRarity, String> colors = Map.of();

    BlockRarity(String defaultLabel, String defaultColor) {
        this.defaultLabel = defaultLabel;
        this.defaultColor = defaultColor;
    }

    /** Pulls labels and colors from messages.yml (gui.rarities.*, colors.rarity-*). */
    public static void configure(MessageService messages) {
        Map<BlockRarity, String> labelMap = new EnumMap<>(BlockRarity.class);
        Map<BlockRarity, String> colorMap = new EnumMap<>(BlockRarity.class);
        for (BlockRarity rarity : values()) {
            String key = rarity.name().toLowerCase(Locale.ROOT);
            labelMap.put(rarity, messages.string("gui.rarities." + key + ".label", Map.of(), rarity.defaultLabel));
            colorMap.put(rarity, messages.string("gui.rarities." + key + ".color", Map.of(), rarity.defaultColor));
        }
        labels = Map.copyOf(labelMap);
        colors = Map.copyOf(colorMap);
    }

    public String displayName() {
        return labels.getOrDefault(this, defaultLabel);
    }

    public String miniMessage() {
        return style(displayName());
    }

    public String style(String content) {
        String color = colors.getOrDefault(this, defaultColor);
        String closeTag = color.startsWith("gradient") ? "gradient" : color;
        return "<" + color + ">" + content + "</" + closeTag + ">";
    }

    public static BlockRarity of(Material material) {
        return ofName(material.name());
    }

    static BlockRarity ofName(String name) {
        if (LEGENDARY_BLOCKS.contains(name)) {
            return LEGENDARY;
        }
        if (EPIC_BLOCKS.contains(name)) {
            return EPIC;
        }
        if (RARE_BLOCKS.contains(name)
                || name.endsWith("_CORAL_BLOCK")
                || name.endsWith("_CORAL")
                || name.endsWith("_CORAL_FAN")
                || name.contains("FROGLIGHT")) {
            return RARE;
        }
        if (UNCOMMON_BLOCKS.contains(name)
                || name.contains("NETHER")
                || name.startsWith("END_")
                || name.startsWith("PURPUR_")
                || name.startsWith("CHORUS_")
                || name.contains("PRISMARINE")
                || name.contains("AMETHYST")
                || name.contains("COPPER")
                || name.contains("SCULK")
                || name.contains("MOSS")
                || name.contains("ICE")
                || name.contains("GLOW_LICHEN")
                || name.contains("DRIPSTONE")
                || name.contains("DEEPSLATE_")
                || name.endsWith("_GLAZED_TERRACOTTA")) {
            return UNCOMMON;
        }
        return COMMON;
    }
}
