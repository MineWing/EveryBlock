package dev.everyblock.service;

import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.model.Contributor;
import dev.everyblock.model.Discovery;
import org.bukkit.Material;

import java.util.Set;
import java.util.HashSet;
import java.util.Collection;
import java.util.concurrent.CompletableFuture;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public final class ProgressService {
    private final ProgressDatabase database;
    private final BlockCatalogue catalogue;
    private volatile Map<Material, Discovery> discoveries;
    private final Set<Material> pending = new HashSet<>();
    private int mutations;

    public ProgressService(ProgressDatabase database, BlockCatalogue catalogue,
                           Map<Material, Discovery> discoveries) {
        this.database = database;
        this.catalogue = catalogue;
        this.discoveries = Map.copyOf(discoveries);
    }

    public CompletableFuture<Boolean> discover(Material material, UUID playerId, String playerName) {
        return discoverMany(List.of(material), playerId, playerName).thenApply(found -> !found.isEmpty());
    }

    // Called on the server thread. Reservations prevent repeated scans from filling the worker queue.
    public CompletableFuture<List<Material>> discoverMany(Collection<Material> materials, UUID playerId, String playerName) {
        if (mutations > 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<Discovery> batch = materials.stream().distinct()
                .filter(catalogue::contains)
                .filter(material -> !discoveries.containsKey(material) && !pending.contains(material))
                .map(material -> new Discovery(material, playerId, playerName, Instant.now())).toList();
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        batch.forEach(discovery -> pending.add(discovery.material()));
        return database.submit(() -> database.insertBatch(batch, false)).handle((saved, failure) -> {
            batch.forEach(discovery -> pending.remove(discovery.material()));
            if (failure != null) {
                throw new java.util.concurrent.CompletionException(failure);
            }
            Map<Material, Discovery> updated = new HashMap<>(discoveries);
            saved.forEach(discovery -> updated.put(discovery.material(), discovery));
            discoveries = Map.copyOf(updated);
            return saved.stream().map(Discovery::material).toList();
        });
    }

    // Admin additions retain their position relative to queued reset/remove commands.
    public CompletableFuture<Boolean> add(Material material, UUID playerId, String playerName) {
        if (!catalogue.contains(material)) {
            return CompletableFuture.completedFuture(false);
        }
        Discovery discovery = new Discovery(material, playerId, playerName, Instant.now());
        return database.submit(() -> database.insert(discovery)).thenApply(inserted -> {
            if (inserted) {
                Map<Material, Discovery> updated = new HashMap<>(discoveries);
                updated.put(material, discovery);
                discoveries = Map.copyOf(updated);
            }
            return inserted;
        });
    }

    public CompletableFuture<Void> reset() {
        mutations++;
        return database.submit(() -> {
            database.clear();
            return null;
        }).handle((ignored, failure) -> {
            mutations--;
            if (failure != null) {
                throw new java.util.concurrent.CompletionException(failure);
            }
            discoveries = Map.of();
            return null;
        });
    }

    public CompletableFuture<Boolean> remove(Material material) {
        mutations++;
        return database.submit(() -> database.remove(material)).handle((removed, failure) -> {
            mutations--;
            if (failure != null) {
                throw new java.util.concurrent.CompletionException(failure);
            }
            Map<Material, Discovery> updated = new HashMap<>(discoveries);
            updated.remove(material);
            discoveries = Map.copyOf(updated);
            return removed;
        });
    }

    public boolean found(Material material) {
        return discoveries.containsKey(material);
    }

    public Optional<Discovery> discovery(Material material) {
        return Optional.ofNullable(discoveries.get(material));
    }

    public int collectedCount() {
        return (int) discoveries.keySet().stream().filter(catalogue::contains).count();
    }

    public int totalCount() {
        return catalogue.blocks().size();
    }

    public int remainingCount() {
        return Math.max(0, totalCount() - collectedCount());
    }

    public List<Contributor> contributors() {
        record Mutable(UUID id, String name, int amount, Instant latest) {
            Mutable add(String newName, Instant at) {
                return at.isAfter(latest) ? new Mutable(id, newName, amount + 1, at)
                        : new Mutable(id, name, amount + 1, latest);
            }
        }
        Map<UUID, Mutable> totals = new HashMap<>();
        discoveries.values().stream().filter(discovery -> catalogue.contains(discovery.material()))
                .forEach(discovery -> totals.compute(discovery.playerId(), (id, existing) -> existing == null
                        ? new Mutable(id, discovery.playerName(), 1, discovery.discoveredAt())
                        : existing.add(discovery.playerName(), discovery.discoveredAt())));
        return totals.values().stream()
                .map(value -> new Contributor(value.id(), value.name(), value.amount()))
                .sorted(Comparator.comparingInt(Contributor::amount).reversed()
                        .thenComparing(Contributor::playerName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    public int contribution(UUID playerId) {
        return (int) discoveries.values().stream()
                .filter(discovery -> catalogue.contains(discovery.material()))
                .filter(discovery -> discovery.playerId().equals(playerId))
                .count();
    }

    public Optional<Discovery> latest() {
        return discoveries.values().stream()
                .filter(discovery -> catalogue.contains(discovery.material()))
                .max(Comparator.comparing(Discovery::discoveredAt));
    }

    public List<Discovery> recent() {
        return discoveries.values().stream()
                .filter(discovery -> catalogue.contains(discovery.material()))
                .sorted(Comparator.comparing(Discovery::discoveredAt).reversed())
                .toList();
    }

    public List<Material> missing() {
        List<Material> result = new ArrayList<>();
        for (Material block : catalogue.blocks()) {
            if (!found(block)) {
                result.add(block);
            }
        }
        return List.copyOf(result);
    }
}
