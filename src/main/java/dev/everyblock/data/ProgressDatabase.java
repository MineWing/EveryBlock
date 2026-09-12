package dev.everyblock.data;

import dev.everyblock.model.Discovery;
import org.bukkit.Material;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class ProgressDatabase implements AutoCloseable {
    private final Path path;
    private Connection connection;
    private ExecutorService worker;
    private Executor completions;

    // The executor must deliver callbacks in FIFO order on the server thread.
    public void startWorker(Executor completions) {
        this.completions = completions;
        worker = Executors.newSingleThreadExecutor(r -> new Thread(r, "EveryBlock-database"));
    }

    public <T> CompletableFuture<T> submit(Callable<T> operation) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                T value;
                try {
                    value = operation.call();
                } catch (Exception failure) {
                    Logger.getLogger("EveryBlock").log(java.util.logging.Level.SEVERE,
                            "Database operation failed", failure);
                    dispatch(() -> future.completeExceptionally(failure));
                    return;
                }
                dispatch(() -> future.complete(value));
            });
        } catch (RejectedExecutionException failure) {
            future.completeExceptionally(failure);
        }
        return future;
    }

    private void dispatch(Runnable completion) {
        try {
            completions.execute(completion);
        } catch (RuntimeException failure) {
            // During shutdown the scheduler can reject work. Completing the future here
            // would run its game-state callbacks on this database thread. Persistence has
            // already finished; leave callbacks undelivered when the server is unavailable.
            Logger.getLogger("EveryBlock").log(java.util.logging.Level.WARNING,
                    "Could not deliver database completion to the server thread", failure);
        }
    }

    public ProgressDatabase(Path path) {
        this.path = path;
    }

    public void open() throws Exception {
        Files.createDirectories(path.getParent());
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA journal_mode=WAL");
            statement.execute("PRAGMA synchronous=NORMAL");
            statement.execute("PRAGMA busy_timeout=5000");
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS discoveries (
                        material TEXT PRIMARY KEY,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        discovered_at INTEGER NOT NULL
                    )
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS item_discoveries (
                        material TEXT PRIMARY KEY,
                        player_uuid TEXT NOT NULL,
                        player_name TEXT NOT NULL,
                        discovered_at INTEGER NOT NULL
                    )
                    """);
        }
    }

    public Map<Material, Discovery> load() throws SQLException {
        return loadFrom("discoveries");
    }

    public Map<Material, Discovery> loadItems() throws SQLException {
        return loadFrom("item_discoveries");
    }

    private Map<Material, Discovery> loadFrom(String table) throws SQLException {
        Map<Material, Discovery> result = new LinkedHashMap<>();
        List<String> invalid = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT material, player_uuid, player_name, discovered_at FROM " + table)) {
            while (rows.next()) {
                Material material = Material.matchMaterial(rows.getString("material"));
                if (material == null) {
                    continue;
                }
                try {
                    UUID playerId = UUID.fromString(rows.getString("player_uuid"));
                    result.put(material, new Discovery(material, playerId,
                            rows.getString("player_name"), Instant.ofEpochMilli(rows.getLong("discovered_at"))));
                } catch (IllegalArgumentException invalidId) {
                    invalid.add(rows.getString("material"));
                }
            }
        }
        for (String material : invalid) {
            quarantine(table, material);
        }
        return result;
    }

    private void quarantine(String table, String material) throws SQLException {
        String copySql = switch (table) {
            case "discoveries" -> """
                    INSERT INTO invalid_discoveries
                    SELECT ?, material, player_uuid, player_name, discovered_at
                    FROM discoveries WHERE material = ?
                    """;
            case "item_discoveries" -> """
                    INSERT INTO invalid_discoveries
                    SELECT ?, material, player_uuid, player_name, discovered_at
                    FROM item_discoveries WHERE material = ?
                    """;
            default -> throw new IllegalArgumentException("Unknown discovery table: " + table);
        };
        String removeSql = switch (table) {
            case "discoveries" -> "DELETE FROM discoveries WHERE material = ?";
            case "item_discoveries" -> "DELETE FROM item_discoveries WHERE material = ?";
            default -> throw new IllegalArgumentException("Unknown discovery table: " + table);
        };
        try (Statement schema = connection.createStatement()) {
            schema.executeUpdate("CREATE TABLE IF NOT EXISTS invalid_discoveries (source_table TEXT, material TEXT, player_uuid TEXT, player_name TEXT, discovered_at INTEGER)");
        }
        connection.setAutoCommit(false);
        try (PreparedStatement copy = connection.prepareStatement(copySql);
             PreparedStatement remove = connection.prepareStatement(removeSql)) {
            copy.setString(1, table);
            copy.setString(2, material);
            copy.executeUpdate();
            remove.setString(1, material);
            remove.executeUpdate();
            connection.commit();
            Logger.getLogger("EveryBlock").warning("Quarantined malformed discovery in " + table + ": " + material);
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public List<Discovery> insertBatch(List<Discovery> discoveries, boolean items) throws SQLException {
        List<Discovery> inserted = new ArrayList<>();
        connection.setAutoCommit(false);
        try {
            for (Discovery discovery : discoveries) {
                if (insertInto(items ? "item_discoveries" : "discoveries", discovery)) {
                    inserted.add(discovery);
                }
            }
            connection.commit();
            return List.copyOf(inserted);
        } catch (SQLException failure) {
            connection.rollback();
            throw failure;
        } finally {
            connection.setAutoCommit(true);
        }
    }

    public boolean insert(Discovery discovery) throws SQLException {
        return insertInto("discoveries", discovery);
    }

    public boolean insertItem(Discovery discovery) throws SQLException {
        return insertInto("item_discoveries", discovery);
    }

    private boolean insertInto(String table, Discovery discovery) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT OR IGNORE INTO %s(material, player_uuid, player_name, discovered_at)
                VALUES (?, ?, ?, ?)
                """.formatted(table))) {
            statement.setString(1, discovery.material().name());
            statement.setString(2, discovery.playerId().toString());
            statement.setString(3, discovery.playerName());
            statement.setLong(4, discovery.discoveredAt().toEpochMilli());
            return statement.executeUpdate() == 1;
        }
    }

    public boolean remove(Material material) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "DELETE FROM discoveries WHERE material = ?")) {
            statement.setString(1, material.name());
            return statement.executeUpdate() > 0;
        }
    }

    public void clear() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM discoveries");
        }
    }

    public void clearItems() throws SQLException {
        try (Statement statement = connection.createStatement()) {
            statement.executeUpdate("DELETE FROM item_discoveries");
        }
    }

    @Override
    public void close() throws SQLException {
        if (worker != null) {
            worker.shutdown();
            boolean interrupted = false;
            while (!worker.isTerminated()) {
                try {
                    worker.awaitTermination(1, TimeUnit.SECONDS);
                } catch (InterruptedException exception) {
                    interrupted = true;
                }
            }
            if (interrupted) {
                Thread.currentThread().interrupt();
            }
        }
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }
}
