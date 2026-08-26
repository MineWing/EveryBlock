package dev.everyblock.hook;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.model.Discovery;
import dev.everyblock.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class EveryBlockExpansion extends PlaceholderExpansion {
    private final EveryBlockPlugin plugin;

    public EveryBlockExpansion(EveryBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "everyblock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Alex";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String params) {
        int collected = plugin.progress().collectedCount();
        int total = plugin.progress().totalCount();
        return switch (params.toLowerCase(java.util.Locale.ROOT)) {
            case "collected" -> Integer.toString(collected);
            case "total" -> Integer.toString(total);
            case "remaining" -> Integer.toString(Math.max(0, total - collected));
            case "percent" -> Text.percent(collected, total);
            case "contribution" -> player == null ? "0"
                    : Integer.toString(plugin.progress().contribution(player.getUniqueId()));
            case "latest" -> plugin.progress().latest().map(Discovery::material)
                    .map(material -> Text.pretty(material.name())).orElse("None");
            case "latest_player" -> plugin.progress().latest().map(Discovery::playerName).orElse("None");
            default -> null;
        };
    }
}
