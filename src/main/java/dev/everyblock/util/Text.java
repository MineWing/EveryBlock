package dev.everyblock.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.text.DecimalFormat;
import java.util.Locale;

public final class Text {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final DecimalFormat PERCENT = new DecimalFormat("0.0");
    private static volatile String accentColor = "#f7a48d";

    private Text() {
    }

    /** Sets the brand accent color (colors.primary in messages.yml) used by {@link #accentColor()}. */
    public static void configureAccent(String color) {
        accentColor = color == null || color.isBlank() ? "#f7a48d" : color;
    }

    public static String accentColor() {
        return accentColor;
    }

    public static Component parse(String input) {
        return MINI_MESSAGE.deserialize(input == null ? "" : input)
                .decoration(TextDecoration.ITALIC, false);
    }

    public static String escape(String input) {
        return MINI_MESSAGE.escapeTags(input == null ? "" : input);
    }

    public static String pretty(String enumName) {
        String[] words = enumName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (!result.isEmpty()) {
                result.append(' ');
            }
            if (!word.isEmpty()) {
                result.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
            }
        }
        return result.toString();
    }

    public static String percent(int amount, int total) {
        if (total <= 0) {
            return "100.0";
        }
        return PERCENT.format(Math.min(100.0, amount * 100.0 / total));
    }

    public static String progressBar(int amount, int total, int width, String filledColor, String emptyColor) {
        double ratio = total <= 0 ? 1.0 : Math.clamp((double) amount / total, 0.0, 1.0);
        int filled = (int) Math.round(ratio * width);
        String filledClose = closeTag(filledColor);
        String emptyClose = closeTag(emptyColor);
        return "<" + filledColor + ">" + "■".repeat(filled) + "</" + filledClose + ">"
                + "<" + emptyColor + ">" + "□".repeat(Math.max(0, width - filled)) + "</" + emptyClose + ">";
    }

    private static String closeTag(String color) {
        return color.startsWith("gradient") ? "gradient" : color;
    }

    public static String durationTicks(long ticks) {
        long seconds = Math.max(0, ticks) / 20;
        long hours = seconds / 3600;
        long minutes = seconds % 3600 / 60;
        long remainder = seconds % 60;
        if (hours > 0) {
            return hours + "h " + minutes + "m " + remainder + "s";
        }
        if (minutes > 0) {
            return minutes + "m " + remainder + "s";
        }
        return remainder + "s";
    }
}
