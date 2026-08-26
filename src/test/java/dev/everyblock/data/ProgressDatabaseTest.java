package dev.everyblock.data;

import dev.everyblock.model.Discovery;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProgressDatabaseTest {
    @Test
    void storesEachMaterialOnlyOnceAndSupportsReset() throws Exception {
        Path path = Path.of("target/test-data/progress-database-test.db").toAbsolutePath();
        UUID player = UUID.fromString("c36baec5-3a07-42c9-b22b-8c83c62d14f9");
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            database.clear();
            database.clearItems();
            Discovery discovery = new Discovery(Material.STONE, player, "Alex", Instant.ofEpochMilli(1234));

            assertTrue(database.insert(discovery));
            assertFalse(database.insert(discovery));
            assertEquals(discovery, database.load().get(Material.STONE));
            assertTrue(database.remove(Material.STONE));
            assertFalse(database.remove(Material.STONE));

            database.insert(discovery);
            database.clear();
            assertTrue(database.load().isEmpty());

            assertTrue(database.insertItem(discovery));
            assertFalse(database.insertItem(discovery));
            assertEquals(discovery, database.loadItems().get(Material.STONE));
            database.clearItems();
            assertTrue(database.loadItems().isEmpty());
        }
    }
}
