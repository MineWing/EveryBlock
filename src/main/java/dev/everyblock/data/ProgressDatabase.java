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

public final class ProgressDatabase implements AutoCloseable {
    private final Path path;
    private Connection connection;

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
                } catch (IllegalArgumentException ignored) {
                    // A malformed legacy row is ignored without preventing the plugin from starting.
                }
            }
        }
        return result;
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
        if (connection != null) {
            connection.close();
            connection = null;
        }
    }
}
