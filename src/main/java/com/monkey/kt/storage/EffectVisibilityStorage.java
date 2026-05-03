package com.monkey.kt.storage;

import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class EffectVisibilityStorage {

    private static final ConcurrentHashMap<UUID, Boolean> visibilityMap = new ConcurrentHashMap<>();
    private static final ThreadLocal<UUID> currentKiller = new ThreadLocal<>();
    private static HikariDataSource dataSource;
    private static DatabaseDialect dialect;
    private static final Logger logger = Logger.getLogger(EffectVisibilityStorage.class.getName());

    public static void init(HikariDataSource ds, DatabaseDialect dbDialect) {
        dataSource = ds;
        dialect = dbDialect;
        loadAll();
    }

    private static void loadAll() {
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery("SELECT uuid, enabled FROM effect_visibility")) {
                while (rs.next()) {
                    UUID uuid = UUID.fromString(rs.getString("uuid"));
                    visibilityMap.put(uuid, rs.getBoolean("enabled"));
                }
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error loading effect visibility data", e);
            }
        });
    }

    public static boolean isEnabled(UUID uuid) {
        return visibilityMap.getOrDefault(uuid, true);
    }

    public static void setEnabled(UUID uuid, boolean enabled) {
        visibilityMap.put(uuid, enabled);
        CompletableFuture.runAsync(() -> {
            try (Connection conn = dataSource.getConnection();
                 PreparedStatement ps = conn.prepareStatement(dialect.getUpsertVisibilityQuery())) {
                ps.setString(1, uuid.toString());
                ps.setBoolean(2, enabled);
                ps.executeUpdate();
            } catch (SQLException e) {
                logger.log(Level.SEVERE, "Error saving effect visibility for player " + uuid, e);
            }
        });
    }

    public static void toggle(UUID uuid) {
        setEnabled(uuid, !isEnabled(uuid));
    }

    public static void setCurrentKiller(UUID killerUuid) {
        currentKiller.set(killerUuid);
    }

    public static UUID getCurrentKiller() {
        return currentKiller.get();
    }

    public static void clearCurrentKiller() {
        currentKiller.remove();
    }
}
