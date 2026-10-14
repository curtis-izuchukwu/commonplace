package com.commonplace.service;

import com.commonplace.repository.DatabaseManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

public class DataManagementService {

    public void exportDatabase(Path targetPath) throws IOException {
        Path target = targetPath.toAbsolutePath();
        if (target.equals(Path.of(DatabaseManager.DB_PATH).toAbsolutePath())) {
            throw new IOException("Choose a backup location outside the active database.");
        }
        Path temporary = Files.createTempFile(target.getParent(), "commonplace-backup-", ".db");
        try {
            Files.delete(temporary); // VACUUM INTO requires a nonexistent or empty destination.
            try (Connection conn = DatabaseManager.connect(); Statement stmt = conn.createStatement()) {
                stmt.execute("VACUUM INTO '" + temporary.toString().replace("'", "''") + "'");
            } catch (SQLException e) {
                throw new IOException("Could not snapshot the local database.", e);
            }
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    public void importDatabase(Path sourcePath) throws IOException {
        Path target = Path.of(DatabaseManager.DB_PATH).toAbsolutePath();
        if (sourcePath.toAbsolutePath().equals(target)) throw new IOException("Choose an exported backup to import.");
        Files.createDirectories(target.getParent());
        Path temporary = Files.createTempFile(target.getParent(), "commonplace-import-", ".db");
        try {
            Files.copy(sourcePath, temporary, StandardCopyOption.REPLACE_EXISTING);
            // Migrate the candidate before replacing the active database. Failed validation is harmless.
            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + temporary);
                 Statement stmt = conn.createStatement()) {
                try (var tables = stmt.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='modules'")) {
                    if (!tables.next()) throw new SQLException("The backup does not contain Commonplace study data.");
                }
                try (var integrity = stmt.executeQuery("PRAGMA integrity_check")) {
                    if (!integrity.next() || !"ok".equals(integrity.getString(1))) throw new SQLException("The backup is damaged.");
                }
                stmt.execute("PRAGMA foreign_keys = ON");
                DatabaseManager.initialiseTables(conn);
                try (var violations = stmt.executeQuery("PRAGMA foreign_key_check")) {
                    if (violations.next()) throw new SQLException("The backup has inconsistent study relationships.");
                }
            } catch (SQLException e) {
                throw new IOException("Could not read or upgrade this Commonplace backup.", e);
            }
            if (Files.exists(target)) exportDatabase(target.resolveSibling("appdata.before-import.db"));
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    public void clearCurrentAccountData() throws SQLException {
        long userId = AccountSession.currentUserId();

        DatabaseManager.transaction(
                () -> {
                    try (Connection conn = DatabaseManager.connect()) {
                        for (String table :
                                java.util.List.of("xp_events", "recommendation_actions")) {
                            try (PreparedStatement stmt =
                                    conn.prepareStatement(
                                            "DELETE FROM " + table + " WHERE user_id = ?")) {
                                stmt.setLong(1, userId);
                                stmt.executeUpdate();
                            }
                        }
                        try (PreparedStatement stmt =
                                conn.prepareStatement("DELETE FROM modules WHERE user_id = ?;")) {

                            stmt.setLong(1, userId);
                            stmt.executeUpdate();
                        }

                        try (PreparedStatement stmt =
                                conn.prepareStatement(
                                        "DELETE FROM user_stats WHERE user_id = ?;")) {

                            stmt.setLong(1, userId);
                            stmt.executeUpdate();
                        }

                        try (PreparedStatement stmt =
                                conn.prepareStatement(
                                        """
                                        INSERT INTO user_stats
                                            (user_id, xp, streak_count, worksheet_interval_days)
                                        VALUES
                                            (?, 0, 0, 1);
                                        """)) {

                            stmt.setLong(1, userId);
                            stmt.executeUpdate();
                        }
                    }
                    return null;
                });
    }
}
