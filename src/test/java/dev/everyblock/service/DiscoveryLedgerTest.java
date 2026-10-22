package dev.everyblock.service;

import dev.everyblock.data.ProgressDatabase;
import dev.everyblock.model.Contributor;
import dev.everyblock.model.Discovery;
import org.bukkit.Material;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DiscoveryLedgerTest {
    private static final UUID ALEX = UUID.randomUUID();
    private static final UUID SAM = UUID.randomUUID();

    private ProgressDatabase database;
    private BlockCatalogue catalogue;
    private volatile List<Material> blocks;

    @BeforeEach
    void setUp() throws Exception {
        database = mock(ProgressDatabase.class);
        when(database.submit(any())).thenAnswer(call -> {
            Callable<?> operation = call.getArgument(0);
            try {
                return CompletableFuture.completedFuture(operation.call());
            } catch (Exception failure) {
                return CompletableFuture.failedFuture(failure);
            }
        });
        when(database.insertBatch(anyList(), anyBoolean())).thenAnswer(call -> call.getArgument(0));
        when(database.insert(any())).thenReturn(true);
        when(database.remove(any())).thenReturn(true);
        catalogue = mock(BlockCatalogue.class);
        blocks = List.of(Material.STONE, Material.DIRT, Material.SAND, Material.GRAVEL);
        when(catalogue.blocks()).thenAnswer(call -> blocks);
        when(catalogue.contains(any())).thenAnswer(call -> blocks.contains(call.<Material>getArgument(0)));
    }

    private static Discovery discovery(Material material, UUID player, String name, long at) {
        return new Discovery(material, player, name, Instant.ofEpochMilli(at));
    }

    @Test
    void derivedCountsAreCachedUntilDiscoveriesOrCatalogueChange() {
        ProgressService progress = new ProgressService(database, catalogue, Map.of(
                Material.STONE, discovery(Material.STONE, ALEX, "Alex", 1),
                Material.DIRT, discovery(Material.DIRT, SAM, "Sam", 2),
                Material.SAND, discovery(Material.SAND, ALEX, "Alex", 3)));

        assertEquals(3, progress.collectedCount());
        assertSame(progress.summary(), progress.summary());
        assertEquals(2, progress.contribution(ALEX));
        assertEquals(0, progress.contribution(UUID.randomUUID()));
        assertEquals(Material.SAND, progress.latest().orElseThrow().material());
        assertEquals(List.of(new Contributor(ALEX, "Alex", 2), new Contributor(SAM, "Sam", 1)),
                progress.contributors());

        DiscoveryLedger.Summary before = progress.summary();
        assertTrue(progress.discover(Material.GRAVEL, SAM, "Sam").join());
        assertNotSame(before, progress.summary());
        assertEquals(4, progress.collectedCount());
        assertEquals(0, progress.remainingCount());
        assertEquals(Material.GRAVEL, progress.latest().orElseThrow().material());

        blocks = List.of(Material.STONE, Material.DIRT);
        assertEquals(2, progress.collectedCount());
        assertEquals(2, progress.totalCount());
        assertEquals(1, progress.contribution(ALEX));
        assertEquals(Material.DIRT, progress.latest().orElseThrow().material());
        assertEquals(List.of(Material.DIRT, Material.STONE),
                progress.recent().stream().map(Discovery::material).toList());

        assertTrue(progress.remove(Material.DIRT).join());
        assertEquals(1, progress.collectedCount());
        assertEquals(List.of(Material.DIRT), progress.missing());
    }

    @Test
    void eachChallengeWritesToItsOwnTable() throws Exception {
        ItemCatalogue items = mock(ItemCatalogue.class);
        when(items.contains(Material.STICK)).thenReturn(true);
        when(items.items()).thenReturn(List.of(Material.STICK));
        ItemProgressService itemProgress = new ItemProgressService(database, items, Map.of());
        ProgressService progress = new ProgressService(database, catalogue, Map.of());

        assertTrue(itemProgress.discover(Material.STICK, ALEX, "Alex").join());
        verify(database).insertBatch(anyList(), eq(true));
        assertEquals(1, itemProgress.collectedCount());
        assertEquals("Alex", itemProgress.discovery(Material.STICK).playerName());
        assertTrue(progress.discover(Material.STONE, ALEX, "Alex").join());
        verify(database).insertBatch(anyList(), eq(false));

        itemProgress.reset().join();
        verify(database).clearItems();
        verify(database, never()).clear();
        assertEquals(0, itemProgress.collectedCount());
        assertEquals(1, progress.collectedCount());
        progress.reset().join();
        verify(database).clear();
        assertEquals(0, progress.collectedCount());
    }

    @Test
    void concurrentReadersAlwaysSeeAConsistentSnapshot() throws Exception {
        List<Material> many = Arrays.stream(Material.values())
                .filter(material -> !material.name().startsWith("LEGACY_"))
                .limit(200).toList();
        // A real catalogue: mocks record every call, which concurrent spinning readers would flood.
        BlockCatalogue real = new BlockCatalogue(null);
        for (var entry : Map.of("blockSet", (Object) java.util.Set.copyOf(many), "blocks", many).entrySet()) {
            var field = BlockCatalogue.class.getDeclaredField(entry.getKey());
            field.setAccessible(true);
            field.set(real, entry.getValue());
        }
        ProgressService progress = new ProgressService(database, real, Map.of());
        AtomicBoolean done = new AtomicBoolean();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        List<Thread> readers = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            Thread reader = new Thread(() -> {
                int last = 0;
                try {
                    while (!done.get()) {
                        DiscoveryLedger.Summary summary = progress.summary();
                        assertEquals(summary.recent().size(), summary.collected());
                        int collected = progress.collectedCount();
                        assertTrue(collected >= last, "counts never go backwards");
                        last = collected;
                    }
                } catch (Throwable error) {
                    failure.compareAndSet(null, error);
                }
            });
            reader.start();
            readers.add(reader);
        }
        for (Material material : many) {
            progress.discover(material, ALEX, "Alex").join();
        }
        done.set(true);
        for (Thread reader : readers) {
            reader.join();
        }
        assertNull(failure.get());
        assertEquals(many.size(), progress.collectedCount());
        assertEquals(many.size(), progress.contribution(ALEX));
    }
}
