package dev.everyblock.util;

import org.junit.jupiter.api.Test;
import net.kyori.adventure.text.format.TextDecoration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextTest {
    @Test
    void prettifiesMaterialNames() {
        assertEquals("Waxed Oxidized Cut Copper Stairs",
                Text.pretty("WAXED_OXIDIZED_CUT_COPPER_STAIRS"));
    }

    @Test
    void formatsProgressSafely() {
        assertEquals("25.0", Text.percent(1, 4));
        assertEquals("100.0", Text.percent(0, 0));
        assertEquals("<#f7a48d>■■■■■</#f7a48d><dark_gray>□□□□□</dark_gray>",
                Text.progressBar(1, 2, 10, "#f7a48d", "dark_gray"));
        assertEquals("<gradient:#C084FC:#8B5CF6>■■■■■</gradient><dark_gray>□□□□□</dark_gray>",
                Text.progressBar(1, 2, 10, "gradient:#C084FC:#8B5CF6", "dark_gray"));
    }

    @Test
    void formatsPercentOnConcurrentPlaceholderThreads() {
        java.util.stream.IntStream.range(0, 10_000).parallel().forEach(index -> {
            int amount = index % 4;
            String expected = switch (amount) {
                case 0 -> "0.0";
                case 1 -> "25.0";
                case 2 -> "50.0";
                default -> "75.0";
            };
            assertEquals(expected, Text.percent(amount, 4));
        });
    }

    @Test
    void escapesPlayerControlledMiniMessage() {
        assertEquals("\\<red>Alex\\</red>", Text.escape("<red>Alex</red>"));
    }

    @Test
    void disablesInheritedMinecraftItalics() {
        assertEquals(TextDecoration.State.FALSE,
                Text.parse("<#f7a48d>Blocks</#f7a48d>").decoration(TextDecoration.ITALIC));
    }

    @Test
    void formatsMinecraftPlaytimeTicks() {
        assertEquals("1h 2m 3s", Text.durationTicks(74_460));
        assertEquals("45s", Text.durationTicks(900));
    }
}
