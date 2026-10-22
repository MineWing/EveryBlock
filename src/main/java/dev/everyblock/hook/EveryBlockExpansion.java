package dev.everyblock.hook;

import dev.everyblock.EveryBlockPlugin;
import dev.everyblock.model.Discovery;
import dev.everyblock.service.ProgressService;
import dev.everyblock.util.Text;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;

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
        // PlaceholderAPI may call this off the server thread; ProgressService reads are snapshot-based.
        ProgressService progress = plugin.progress();
        return switch (params.toLowerCase(Locale.ROOT)) {
            case "collected" -> Integer.toString(progress.collectedCount());
            case "total" -> Integer.toString(progress.totalCount());
            case "remaining" -> Integer.toString(progress.remainingCount());
            case "percent" -> Text.percent(progress.collectedCount(), progress.totalCount());
            case "contribution" -> player == null ? "0"
                    : Integer.toString(progress.contribution(player.getUniqueId()));
            case "latest" -> progress.latest().map(Discovery::material)
                    .map(material -> Text.pretty(material.name())).orElse("None");
            case "latest_player" -> progress.latest().map(Discovery::playerName).orElse("None");
            default -> null;
        };
    }
}
