package dev.everyblock.service;

import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.model.Discovery;
import org.bukkit.Material;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class ProgressService extends DiscoveryLedger {
    public ProgressService(ProgressDatabase database, BlockCatalogue catalogue,
                           Map<Material, Discovery> discoveries) {
        super(database, false, catalogue::contains, catalogue::blocks, discoveries);
    }

    // Admin additions retain their position relative to queued reset/remove commands.
    public CompletableFuture<Boolean> add(Material material, UUID playerId, String playerName) {
        if (!catalogued(material)) {
            return CompletableFuture.completedFuture(false);
        }
        Discovery discovery = new Discovery(material, playerId, playerName, Instant.now());
        return database().submit(() -> database().insert(discovery)).thenApply(inserted -> {
            if (inserted) {
                remember(discovery);
            }
            return inserted;
        });
    }

    public CompletableFuture<Boolean> remove(Material material) {
        return exclusive(() -> database().remove(material), removed -> forget(material));
    }

    public Optional<Discovery> discovery(Material material) {
        return Optional.ofNullable(get(material));
    }

    public int remainingCount() {
        return Math.max(0, totalCount() - collectedCount());
    }

    public int contribution(UUID playerId) {
        return summary().contributions().getOrDefault(playerId, 0);
    }

    public Optional<Discovery> latest() {
        return Optional.ofNullable(summary().latest());
    }

    public List<Material> missing() {
        List<Material> result = new ArrayList<>();
        for (Material block : catalogue()) {
            if (!found(block)) {
                result.add(block);
            }
        }
        return List.copyOf(result);
    }
}
