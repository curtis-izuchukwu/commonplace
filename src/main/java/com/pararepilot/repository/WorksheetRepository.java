package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.Worksheet;
import com.pararepilot.util.DateUtils;

public class WorksheetRepository {

    public Worksheet create(
            long topicId,
            String title,
            String description,
            DifficultyLevel difficulty,
            ImportanceLevel importance
    ) throws SQLException {

        String sql = """
                INSERT INTO worksheets
                    (topic_id, title, description, difficulty, importance, source, created_at)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?);
                """;

        LocalDateTime now = DateUtils.now();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, topicId);
            stmt.setString(2, title.trim());
            stmt.setString(3, blankToNull(description));
            stmt.setString(4, difficulty.name());
            stmt.setString(5, importance.name());
            stmt.setString(6, "manual");
            stmt.setString(7, DateUtils.toDatabaseDateTime(now));

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1))
                            .orElseThrow(() -> new SQLException("Created worksheet could not be reloaded."));
                }
            }

            throw new SQLException("Creating worksheet failed; no ID returned.");
        }
    }

    public Optional<Worksheet> findById(long id) throws SQLException {
        String sql = """
                SELECT id, topic_id, title, description, difficulty, importance, source,
                       created_at, last_attempted_at, times_attempted,
                       latest_score_percent, average_score_percent, failure_streak
                FROM worksheets
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

    public List<Worksheet> findByTopicId(long topicId) throws SQLException {
        String sql = """
                SELECT id, topic_id, title, description, difficulty, importance, source,
                       created_at, last_attempted_at, times_attempted,
                       latest_score_percent, average_score_percent, failure_streak
                FROM worksheets
                WHERE topic_id = ?
                ORDER BY created_at DESC;
                """;

        List<Worksheet> worksheets = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, topicId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    worksheets.add(mapRow(rs));
                }
            }
        }

        return worksheets;
    }

    public int countByTopicId(long topicId) throws SQLException {
        String sql = "SELECT COUNT(*) AS worksheet_count FROM worksheets WHERE topic_id = ?;";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, topicId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("worksheet_count");
            }
        }
    }

    public void deleteById(long id) throws SQLException {
        String sql = "DELETE FROM worksheets WHERE id = ?;";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private Worksheet mapRow(ResultSet rs) throws SQLException {
        return new Worksheet(
                rs.getLong("id"),
                rs.getLong("topic_id"),
                rs.getString("title"),
                rs.getString("description"),
                DifficultyLevel.valueOf(rs.getString("difficulty")),
                ImportanceLevel.valueOf(rs.getString("importance")),
                rs.getString("source"),
                DateUtils.fromDatabaseDateTime(rs.getString("created_at")),
                DateUtils.fromDatabaseDateTime(rs.getString("last_attempted_at")),
                rs.getInt("times_attempted"),
                getNullableDouble(rs, "latest_score_percent"),
                getNullableDouble(rs, "average_score_percent"),
                rs.getInt("failure_streak")
        );
    }

    private Double getNullableDouble(ResultSet rs, String columnName) throws SQLException {
        double value = rs.getDouble(columnName);
        return rs.wasNull() ? null : value;
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}