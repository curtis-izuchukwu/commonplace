package com.commonplace.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.commonplace.repository.DatabaseManager;

public class DataManagementService {

    public void exportDatabase(Path targetPath) throws IOException {
        Files.copy(
                Path.of(DatabaseManager.DB_PATH),
                targetPath,
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    public void importDatabase(Path sourcePath) throws IOException {
        Files.copy(
                sourcePath,
                Path.of(DatabaseManager.DB_PATH),
                StandardCopyOption.REPLACE_EXISTING
        );
    }

    public void clearCurrentAccountData() throws SQLException {
        long userId = AccountSession.currentUserId();

        try (Connection conn = DatabaseManager.connect()) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM modules WHERE user_id = ?;")) {

                stmt.setLong(1, userId);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "DELETE FROM user_stats WHERE user_id = ?;")) {

                stmt.setLong(1, userId);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                    INSERT INTO user_stats
                        (user_id, xp, streak_count, worksheet_interval_days)
                    VALUES
                        (?, 0, 0, 1);
                    """)) {

                stmt.setLong(1, userId);
                stmt.executeUpdate();
            }
        }
    }
}
