package dev.everyblock.service;

import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.model.Contributor;
import dev.everyblock.model.Discovery;
import org.bukkit.Material;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * Shared bookkeeping for one challenge's discoveries. Mutations happen on the server thread;
 * reads may come from any thread (e.g. asynchronous PlaceholderAPI requests), so all state that
 * readers see is published as immutable snapshots through volatile fields.
 */
abstract class DiscoveryLedger {
    private final ProgressDatabase database;
    private final boolean items;
    private final Predicate<Material> catalogued;
    private final Supplier<List<Material>> catalogue;
    private volatile Map<Material, Discovery> discoveries;
    private volatile Summary summary;
    private final Set<Material> pending = new HashSet<>();
    private int mutations;

    DiscoveryLedger(ProgressDatabase database, boolean items, Predicate<Material> catalogued,
                    Supplier<List<Material>> catalogue, Map<Material, Discovery> discoveries) {
        this.database = database;
        this.items = items;
        this.catalogued = catalogued;
        this.catalogue = catalogue;
        this.discoveries = Map.copyOf(discoveries);
    }

    // Called on the server thread. Reservations prevent repeated scans from filling the worker queue.
    public CompletableFuture<List<Material>> discoverMany(Collection<Material> materials, UUID playerId, String playerName) {
        if (mutations > 0) {
            return CompletableFuture.completedFuture(List.of());
        }
        List<Discovery> batch = materials.stream().distinct()
                .filter(catalogued)
                .filter(material -> !discoveries.containsKey(material) && !pending.contains(material))
                .map(material -> new Discovery(material, playerId, playerName, Instant.now())).toList();
        if (batch.isEmpty()) {
            return CompletableFuture.completedFuture(List.of());
        }
        batch.forEach(discovery -> pending.add(discovery.material()));
        return database.submit(() -> database.insertBatch(batch, items)).handle((saved, failure) -> {
            batch.forEach(discovery -> pending.remove(discovery.material()));
            if (failure != null) {
                throw new CompletionException(failure);
            }
            Map<Material, Discovery> updated = new HashMap<>(discoveries);
            saved.forEach(discovery -> updated.put(discovery.material(), discovery));
            discoveries = Map.copyOf(updated);
            return saved.stream().map(Discovery::material).toList();
        });
    }

    public CompletableFuture<Boolean> discover(Material material, UUID playerId, String playerName) {
        return discoverMany(List.of(material), playerId, playerName).thenApply(found -> !found.isEmpty());
    }

    public CompletableFuture<Void> reset() {
        return exclusive(() -> {
            if (items) {
                database.clearItems();
            } else {
                database.clear();
            }
            return null;
        }, ignored -> discoveries = Map.of());
    }

    /**
     * Runs a destructive operation on the database worker. While it is queued, scans cannot
     * reserve discoveries, so a stale insert can never resurrect what it removes.
     */
    final <T> CompletableFuture<T> exclusive(Callable<T> operation, Consumer<T> apply) {
        mutations++;
        return database.submit(operation).handle((value, failure) -> {
            mutations--;
            if (failure != null) {
                throw new CompletionException(failure);
            }
            apply.accept(value);
            return value;
        });
    }

    final void remember(Discovery discovery) {
        Map<Material, Discovery> updated = new HashMap<>(discoveries);
        updated.put(discovery.material(), discovery);
        discoveries = Map.copyOf(updated);
    }

    final void forget(Material material) {
        Map<Material, Discovery> updated = new HashMap<>(discoveries);
        updated.remove(material);
        discoveries = Map.copyOf(updated);
    }

    final ProgressDatabase database() {
        return database;
    }

    final boolean catalogued(Material material) {
        return catalogued.test(material);
    }

    final List<Material> catalogue() {
        return catalogue.get();
    }

    public boolean found(Material material) {
        return discoveries.containsKey(material);
    }

    final Discovery get(Material material) {
        return discoveries.get(material);
    }

    public int collectedCount() {
        return summary().collected();
    }

    public int totalCount() {
        return catalogue.get().size();
    }

    public List<Contributor> contributors() {
        return summary().contributors();
    }

    public List<Discovery> recent() {
        return summary().recent();
    }

    final Summary summary() {
        // Read the catalogue list before the discoveries: catalogues publish their membership set
        // before the list, so seeing a new list guarantees the predicate reflects it too.
        List<Material> currentCatalogue = catalogue.get();
        Map<Material, Discovery> current = discoveries;
        Summary cached = summary;
        if (cached != null && cached.discoveries() == current && cached.catalogue() == currentCatalogue) {
            return cached;
        }
        Summary computed = Summary.of(current, currentCatalogue, catalogued);
        summary = computed;
        return computed;
    }

    /** Derived values for one discoveries map and catalogue list, cached until either changes. */
    record Summary(Map<Material, Discovery> discoveries, List<Material> catalogue, int collected,
                   List<Discovery> recent, List<Contributor> contributors, Map<UUID, Integer> contributions) {
        static Summary of(Map<Material, Discovery> discoveries, List<Material> catalogue,
                          Predicate<Material> catalogued) {
            List<Discovery> recent = discoveries.values().stream()
                    .filter(discovery -> catalogued.test(discovery.material()))
                    .sorted(Comparator.comparing(Discovery::discoveredAt).reversed())
                    .toList();

            record Mutable(UUID id, String name, int amount, Instant latest) {
                Mutable add(String newName, Instant at) {
                    return at.isAfter(latest) ? new Mutable(id, newName, amount + 1, at)
                            : new Mutable(id, name, amount + 1, latest);
                }
            }
            Map<UUID, Mutable> totals = new HashMap<>();
            recent.forEach(discovery -> totals.compute(discovery.playerId(), (id, existing) -> existing == null
                    ? new Mutable(id, discovery.playerName(), 1, discovery.discoveredAt())
                    : existing.add(discovery.playerName(), discovery.discoveredAt())));
            List<Contributor> contributors = totals.values().stream()
                    .map(value -> new Contributor(value.id(), value.name(), value.amount()))
                    .sorted(Comparator.comparingInt(Contributor::amount).reversed()
                            .thenComparing(Contributor::playerName, String.CASE_INSENSITIVE_ORDER))
                    .toList();
            Map<UUID, Integer> contributions = new HashMap<>();
            contributors.forEach(contributor -> contributions.put(contributor.playerId(), contributor.amount()));
            return new Summary(discoveries, catalogue, recent.size(), recent, contributors,
                    Map.copyOf(contributions));
        }

        Discovery latest() {
            return recent.isEmpty() ? null : recent.getFirst();
        }
    }
}
