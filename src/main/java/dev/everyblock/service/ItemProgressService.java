package dev.everyblock.service;

import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.model.Discovery;
import org.bukkit.Material;

import java.util.Map;

public final class ItemProgressService extends DiscoveryLedger {
    public ItemProgressService(ProgressDatabase database, ItemCatalogue catalogue,
                               Map<Material, Discovery> discoveries) {
        super(database, true, catalogue::contains, catalogue::items, discoveries);
    }

    public Discovery discovery(Material material) {
        return get(material);
    }
}
