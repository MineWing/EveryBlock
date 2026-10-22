package dev.everyblock.data;

import dev.everyblock.model.Discovery;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class ProgressDatabaseTest {
    private static Path path() {
        return Path.of("target/test-data/database-" + UUID.randomUUID() + ".db").toAbsolutePath();
    }

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

    @Test
    void nullIdentityColumnsAreQuarantinedInsteadOfAbortingStartup() throws Exception {
        Path path = path();
        Files.createDirectories(path.getParent());
        String valid = UUID.randomUUID().toString();
        // Older or hand-edited databases may lack the NOT NULL constraints.
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement()) {
            statement.executeUpdate("CREATE TABLE discoveries (material TEXT PRIMARY KEY, player_uuid TEXT, player_name TEXT, discovered_at INTEGER)");
            statement.executeUpdate("INSERT INTO discoveries VALUES ('STONE', NULL, 'No id', 1)");
            statement.executeUpdate("INSERT INTO discoveries VALUES ('DIRT', '" + valid + "', NULL, 2)");
            statement.executeUpdate("INSERT INTO discoveries VALUES (NULL, '" + valid + "', 'No material', 3)");
            statement.executeUpdate("INSERT INTO discoveries VALUES ('GRASS_BLOCK', '" + valid + "', 'Valid', 4)");
        }
        Logger logger = mock(Logger.class);
        try (ProgressDatabase database = new ProgressDatabase(path, logger)) {
            database.open();
            assertEquals(Set.of(Material.GRASS_BLOCK), database.load().keySet());
            verify(logger, times(2)).warning(startsWith("Quarantined malformed discovery in discoveries"));
            assertTrue(database.load().containsKey(Material.GRASS_BLOCK));
        }
        try (var connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT COUNT(*) FROM invalid_discoveries")) {
            assertTrue(rows.next());
            assertEquals(2, rows.getInt(1));
        }
    }

    @Test
    void workerIsDaemonAndCloseGivesUpOnStuckWrites() throws Exception {
        Logger logger = mock(Logger.class);
        ProgressDatabase database = new ProgressDatabase(path(), logger);
        database.open();
        database.startWorker(Runnable::run);
        assertTrue(database.submit(() -> Thread.currentThread().isDaemon()).join());

        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        database.submit(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException exception) {
                interrupted.countDown();
            }
            return null;
        });
        assertTrue(started.await(5, TimeUnit.SECONDS));
        database.closeTimeout(Duration.ofMillis(200));
        long start = System.nanoTime();
        database.close();
        assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(5), "close must be bounded");
        verify(logger).warning(contains("did not finish"));
        assertTrue(interrupted.await(5, TimeUnit.SECONDS), "abandoned work is interrupted");
    }

    @Test
    void describeUnwrapsFutureWrappers() {
        assertEquals("disk full", ProgressDatabase.describe(
                new CompletionException(new CompletionException(new SQLException("disk full")))));
        assertEquals("IllegalStateException", ProgressDatabase.describe(new IllegalStateException()));
    }
}
