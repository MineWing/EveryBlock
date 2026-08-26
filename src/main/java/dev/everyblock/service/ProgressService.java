package dev.everyblock.service;

import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.model.Contributor;
import dev.everyblock.model.Discovery;
import org.bukkit.Material;

import java.sql.SQLException;
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
    private final Map<Material, Discovery> discoveries;

    public ProgressService(ProgressDatabase database, BlockCatalogue catalogue,
                           Map<Material, Discovery> discoveries) {
        this.database = database;
        this.catalogue = catalogue;
        this.discoveries = new HashMap<>(discoveries);
    }

    public boolean discover(Material material, UUID playerId, String playerName) throws SQLException {
        if (!catalogue.contains(material) || discoveries.containsKey(material)) {
            return false;
        }
        Discovery discovery = new Discovery(material, playerId, playerName, Instant.now());
        if (!database.insert(discovery)) {
            return false;
        }
        discoveries.put(material, discovery);
        return true;
    }

    public boolean remove(Material material) throws SQLException {
        if (!discoveries.containsKey(material) || !database.remove(material)) {
            return false;
        }
        discoveries.remove(material);
        return true;
    }

    public void reset() throws SQLException {
        database.clear();
        discoveries.clear();
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
