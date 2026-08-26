package dev.everyblock.service;

import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.model.Contributor;
import dev.everyblock.model.Discovery;
import org.bukkit.Material;

import java.sql.SQLException;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class ItemProgressService {
    private final ProgressDatabase database;
    private final ItemCatalogue catalogue;
    private final Map<Material, Discovery> discoveries;

    public ItemProgressService(ProgressDatabase database, ItemCatalogue catalogue,
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
        if (!database.insertItem(discovery)) {
            return false;
        }
        discoveries.put(material, discovery);
        return true;
    }

    public boolean found(Material material) {
        return discoveries.containsKey(material);
    }

    public Discovery discovery(Material material) {
        return discoveries.get(material);
    }

    public int collectedCount() {
        return (int) discoveries.keySet().stream().filter(catalogue::contains).count();
    }

    public int totalCount() {
        return catalogue.items().size();
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

    public List<Discovery> recent() {
        return discoveries.values().stream()
                .filter(discovery -> catalogue.contains(discovery.material()))
                .sorted(Comparator.comparing(Discovery::discoveredAt).reversed())
                .toList();
    }

    public void reset() throws SQLException {
        database.clearItems();
        discoveries.clear();
    }
}
