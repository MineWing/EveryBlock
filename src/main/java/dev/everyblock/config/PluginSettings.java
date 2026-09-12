package dev.everyblock.config;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public record PluginSettings(
        ParticipantMode participantMode,
        Set<UUID> allowlist,
        int scanIntervalTicks,
        boolean countCreativeMode,
        Set<String> excludedBlocks,
        List<String> excludedPrefixes,
        Set<String> includedBlocks,
        boolean itemsEnabled,
        Set<String> excludedItems,
        List<String> excludedItemPrefixes,
        Set<String> includedItems,
        Path databasePath,
        String guiTitle,
        String dateFormat,
        List<Integer> milestones
) {
    public PluginSettings withDatabasePath(Path path) {
        return new PluginSettings(participantMode, allowlist, scanIntervalTicks, countCreativeMode,
                excludedBlocks, excludedPrefixes, includedBlocks, itemsEnabled, excludedItems,
                excludedItemPrefixes, includedItems, path, guiTitle, dateFormat, milestones);
    }

    public enum ParticipantMode {
        EVERYONE,
        ALLOWLIST
    }
}
