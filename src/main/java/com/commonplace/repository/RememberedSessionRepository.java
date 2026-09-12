package com.commonplace.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import com.commonplace.model.User;
import com.commonplace.util.DateUtils;

public class RememberedSessionRepository {

    public Optional<User> findRememberedUser() throws SQLException {
        String sql = """
                SELECT users.id,
                       users.username,
                       users.password_hash,
                       users.password_salt,
                       users.created_at,
                       users.updated_at
                FROM remembered_session
                INNER JOIN users ON users.id = remembered_session.user_id
                WHERE remembered_session.id = 1;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return Optional.of(mapUser(rs));
            }
        }

        clear();
        return Optional.empty();
    }

    public void remember(User user) throws SQLException {
        String sql = """
                INSERT INTO remembered_session
                    (id, user_id, updated_at)
                VALUES
                    (1, ?, ?)
                ON CONFLICT(id) DO UPDATE SET
                    user_id = excluded.user_id,
                    updated_at = excluded.updated_at;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, user.id());
            stmt.setString(2, DateUtils.toDatabaseDateTime(DateUtils.now()));
            stmt.executeUpdate();
        }
    }

    public void clear() throws SQLException {
        String sql = """
                DELETE FROM remembered_session
                WHERE id = 1;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.executeUpdate();
        }
    }

    private User mapUser(ResultSet rs) throws SQLException {
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
