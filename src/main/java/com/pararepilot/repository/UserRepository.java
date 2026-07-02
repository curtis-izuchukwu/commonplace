package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Optional;

import com.pararepilot.model.User;
import com.pararepilot.service.PasswordHasher;
import com.pararepilot.util.DateUtils;

public class UserRepository {

    private static final String FALLBACK_USERNAME = "__local__";

    public User create(String username, String passwordHash, String passwordSalt)
            throws SQLException {

        String sql = """
                INSERT INTO users
                    (username, password_hash, password_salt, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?, ?);
                """;

        LocalDateTime now = DateUtils.now();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setString(1, username.trim());
            stmt.setString(2, passwordHash);
            stmt.setString(3, passwordSalt);
            stmt.setString(4, DateUtils.toDatabaseDateTime(now));
            stmt.setString(5, DateUtils.toDatabaseDateTime(now));
            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1))
                            .orElseThrow(() -> new SQLException("Created user could not be reloaded."));
                }
            }

            throw new SQLException("Creating user failed; no ID returned.");
        }
    }

    public Optional<User> findById(long id) throws SQLException {
        String sql = """
                SELECT id, username, password_hash, password_salt, created_at, updated_at
                FROM users
                WHERE id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    public Optional<User> findByUsername(String username) throws SQLException {
        String sql = """
                SELECT id, username, password_hash, password_salt, created_at, updated_at
                FROM users
                WHERE username = ? COLLATE NOCASE;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, username.trim());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }
        }

        return Optional.empty();
    }

    public void updatePassword(long userId, String passwordHash, String passwordSalt)
            throws SQLException {

        String sql = """
                UPDATE users
                SET password_hash = ?,
                    password_salt = ?,
                    updated_at = ?
                WHERE id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, passwordHash);
            stmt.setString(2, passwordSalt);
            stmt.setString(3, DateUtils.toDatabaseDateTime(DateUtils.now()));
            stmt.setLong(4, userId);
            stmt.executeUpdate();
        }
    }

    public int countVisibleUsers() throws SQLException {
        String sql = """
                SELECT COUNT(*) AS user_count
                FROM users
                WHERE username <> ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, FALLBACK_USERNAME);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("user_count");
            }
        }
    }

    public User ensureLocalFallbackUser() throws SQLException {
        Optional<User> existing = findByUsername(FALLBACK_USERNAME);

        if (existing.isPresent()) {
            return existing.get();
        }

        PasswordHasher hasher = new PasswordHasher();
        String salt = hasher.newSalt();
        String hash = hasher.hash("local-only".toCharArray(), salt);
        User user = create(FALLBACK_USERNAME, hash, salt);
        ensureStatsRow(user.id());
        return user;
    }

    public void ensureStatsRow(long userId) throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO user_stats
                    (user_id, xp, streak_count, worksheet_interval_days)
                VALUES
                    (?, 0, 0, 1);
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, userId);
            stmt.executeUpdate();
        }
    }

    public void claimLegacyData(long userId) throws SQLException {
        try (Connection conn = DatabaseManager.connect()) {
            try (PreparedStatement stmt = conn.prepareStatement("""
                    UPDATE modules
                    SET user_id = ?
                    WHERE user_id IS NULL;
                    """)) {

                stmt.setLong(1, userId);
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement("""
                    UPDATE user_stats
                    SET user_id = ?
                    WHERE user_id = 0
                      AND NOT EXISTS (
                          SELECT 1
                          FROM user_stats owned_stats
                          WHERE owned_stats.user_id = ?
                      );
                    """)) {

                stmt.setLong(1, userId);
                stmt.setLong(2, userId);
                stmt.executeUpdate();
            }
        }

        ensureStatsRow(userId);
    }

    private User mapRow(ResultSet rs) throws SQLException {
        return new User(
                rs.getLong("id"),
                rs.getString("username"),
                rs.getString("password_hash"),
                rs.getString("password_salt"),
                DateUtils.fromDatabaseDateTime(rs.getString("created_at")),
                DateUtils.fromDatabaseDateTime(rs.getString("updated_at"))
        );
    }
}
