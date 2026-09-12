package dev.everyblock.data;

import dev.everyblock.model.Discovery;
import dev.everyblock.service.BlockCatalogue;
import dev.everyblock.service.ProgressService;
import dev.everyblock.service.ItemCatalogue;
import dev.everyblock.service.ItemProgressService;
import org.bukkit.Material;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class AsyncProgressTest {
    private Path path() {
        return Path.of("target/test-data/async-" + UUID.randomUUID() + ".db").toAbsolutePath();
    }

    private ProgressService service(ProgressDatabase database) throws Exception {
        BlockCatalogue catalogue = new BlockCatalogue(null);
        var set = BlockCatalogue.class.getDeclaredField("blockSet");
        set.setAccessible(true);
        set.set(catalogue, Set.of(Material.STONE, Material.DIRT));
        return new ProgressService(database, catalogue, Map.of());
    }

    private void finish(BlockingQueue<Runnable> mainThread, CompletableFuture<?> future) throws Exception {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!future.isDone() && System.nanoTime() < deadline) {
            Runnable completion = mainThread.poll(1, TimeUnit.SECONDS);
            if (completion != null) completion.run();
        }
        assertTrue(future.isDone(), "worker must complete without blocking on the main thread");
    }

    @Test
    void contentionDoesNotBlockCallerAndFirstFinderWins() throws Exception {
        Path path = path();
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            database.startWorker(main::add);
            ProgressService progress = service(database);
            UUID first = UUID.randomUUID();
            try (var blocker = DriverManager.getConnection("jdbc:sqlite:" + path);
                 var statement = blocker.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
                var workerStarted = database.submit(() -> null);
                long start = System.nanoTime();
                var saved = progress.discoverMany(List.of(Material.STONE, Material.DIRT), first, "First");
                assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(1));
                assertFalse(saved.isDone());
                assertFalse(progress.found(Material.STONE));
                assertFalse(progress.discover(Material.STONE, UUID.randomUUID(), "Second").join());
                finish(main, workerStarted);
                assertNull(main.poll(150, TimeUnit.MILLISECONDS),
                        "discovery must remain blocked on the worker while SQLite is locked");
                statement.execute("COMMIT");
                finish(main, saved);
                assertEquals(List.of(Material.STONE, Material.DIRT), saved.join());
                assertEquals(first, progress.discovery(Material.STONE).orElseThrow().playerId());
            }
        }
    }

    @Test
    void failuresReleaseReservationsAndBatchRollsBack() throws Exception {
        Path path = path();
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            database.startWorker(main::add);
            ProgressService progress = service(database);
            try (var admin = DriverManager.getConnection("jdbc:sqlite:" + path);
                 var statement = admin.createStatement()) {
                statement.execute("CREATE TRIGGER fail_dirt BEFORE INSERT ON discoveries WHEN NEW.material = 'DIRT' BEGIN SELECT RAISE(ABORT, 'test failure'); END");
                var failed = progress.discoverMany(List.of(Material.STONE, Material.DIRT), UUID.randomUUID(), "First");
                finish(main, failed);
                assertTrue(failed.isCompletedExceptionally());
                assertFalse(progress.found(Material.STONE));
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM discoveries")) {
                    assertTrue(rows.next());
                    assertEquals(0, rows.getInt(1));
                }
                statement.execute("DROP TRIGGER fail_dirt");
                var retry = progress.discover(Material.STONE, UUID.randomUUID(), "Retry");
                finish(main, retry);
                assertTrue(retry.join());
            }
        }
    }

    @Test
    void queuedResetAndRemoveCannotRestoreOldDiscoveries() throws Exception {
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        Path path = path();
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            database.startWorker(main::add);
            ProgressService progress = service(database);
            var discovery = progress.discover(Material.STONE, UUID.randomUUID(), "First");
            var reset = progress.reset();
            assertFalse(progress.discover(Material.DIRT, UUID.randomUUID(), "During reset").join());
            finish(main, reset);
            assertTrue(discovery.join());
            assertFalse(progress.found(Material.STONE));
            var again = progress.discover(Material.STONE, UUID.randomUUID(), "After reset");
            var removed = progress.remove(Material.STONE);
            finish(main, removed);
            assertTrue(again.join());
            assertTrue(removed.join());
            assertFalse(progress.found(Material.STONE));
        }
        try (ProgressDatabase reopened = new ProgressDatabase(path)) {
            reopened.open();
            assertTrue(reopened.load().isEmpty());
        }
    }

    @Test
    void adminAddAfterResetPersistsInSubmissionOrder() throws Exception {
        Path path = path();
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            database.startWorker(main::add);
            ProgressService progress = service(database);
            progress.add(Material.STONE, UUID.randomUUID(), "Before reset");
            progress.reset();
            UUID afterReset = UUID.randomUUID();
            var added = progress.add(Material.STONE, afterReset, "After reset");
            finish(main, added);
            assertTrue(added.join());
            assertEquals(afterReset, progress.discovery(Material.STONE).orElseThrow().playerId());
        }
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            assertEquals("After reset", database.load().get(Material.STONE).playerName());
        }
    }

    @Test
    void itemResetFollowsPendingDiscovery() throws Exception {
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        try (ProgressDatabase database = new ProgressDatabase(path())) {
            database.open();
            database.startWorker(main::add);
            ItemCatalogue catalogue = new ItemCatalogue(null);
            var set = ItemCatalogue.class.getDeclaredField("itemSet");
            set.setAccessible(true);
            set.set(catalogue, Set.of(Material.STONE));
            ItemProgressService progress = new ItemProgressService(database, catalogue, Map.of());
            var discovery = progress.discover(Material.STONE, UUID.randomUUID(), "First");
            assertFalse(progress.discover(Material.STONE, UUID.randomUUID(), "Second").join());
            var reset = progress.reset();
            finish(main, reset);
            assertTrue(discovery.join());
            assertFalse(progress.found(Material.STONE));
        }
    }

    @Test
    void rejectedMainExecutorNeverRunsGameCallbacksOnWorker() throws Exception {
        Path path = path();
        java.util.concurrent.atomic.AtomicBoolean callbackRan = new java.util.concurrent.atomic.AtomicBoolean();
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            database.startWorker(action -> {
                throw new java.util.concurrent.RejectedExecutionException("Server stopped");
            });
            service(database).discover(Material.STONE, UUID.randomUUID(), "Saved")
                    .whenComplete((value, failure) -> callbackRan.set(true));
        }
        assertFalse(callbackRan.get());
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            assertTrue(database.load().containsKey(Material.STONE));
        }
    }

    @Test
    void closeDrainsAcceptedWritesWithoutRunningMainCallbacks() throws Exception {
        Path path = path();
        BlockingQueue<Runnable> main = new LinkedBlockingQueue<>();
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            database.startWorker(main::add);
            service(database).discover(Material.STONE, UUID.randomUUID(), "Saved at shutdown");
        }
        assertFalse(main.isEmpty());
        try (ProgressDatabase reopened = new ProgressDatabase(path)) {
            reopened.open();
            assertTrue(reopened.load().containsKey(Material.STONE));
        }
    }

    @Test
    void quarantinesMalformedRowsInBothChallengesAndAllowsRediscovery() throws Exception {
        Path path = path();
        try (ProgressDatabase database = new ProgressDatabase(path)) {
            database.open();
            try (var editor = DriverManager.getConnection("jdbc:sqlite:" + path);
                 var statement = editor.createStatement()) {
                for (String table : List.of("discoveries", "item_discoveries")) {
                    statement.execute("INSERT INTO " + table + " VALUES ('STONE', 'invalid', 'Old name', 123)");
                }
                assertTrue(database.load().isEmpty());
                assertTrue(database.loadItems().isEmpty());
                Discovery replacement = new Discovery(Material.STONE, UUID.randomUUID(), "New finder", Instant.now());
                assertTrue(database.insert(replacement));
                assertTrue(database.insertItem(replacement));
                try (var rows = statement.executeQuery("SELECT COUNT(*) FROM invalid_discoveries WHERE player_uuid = 'invalid'")) {
                    assertTrue(rows.next());
                    assertEquals(2, rows.getInt(1));
                }
            }
        }
    }
}
