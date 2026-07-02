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

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.Topic;
import com.pararepilot.util.DateUtils;

public class TopicRepository {

    public Topic create(
            long moduleId,
            String name,
            String description,
            ImportanceLevel importance,
            ConfidenceLevel confidence
    ) throws SQLException {

        String sql = """
                INSERT INTO topics
                    (module_id, name, description, importance, confidence, mastery_score, created_at, updated_at)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?);
                """;

        LocalDateTime now = DateUtils.now();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, moduleId);
            stmt.setString(2, name.trim());
            stmt.setString(3, blankToNull(description));
            stmt.setString(4, importance.name());
            stmt.setString(5, confidence.name());
            stmt.setDouble(6, 0.0);
            stmt.setString(7, DateUtils.toDatabaseDateTime(now));
            stmt.setString(8, DateUtils.toDatabaseDateTime(now));

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1))
                            .orElseThrow(() -> new SQLException("Created topic could not be reloaded."));
                }
            }

            throw new SQLException("Creating topic failed; no ID returned.");
        }
    }

    public Optional<Topic> findById(long id) throws SQLException {
        String sql = """
                SELECT id, module_id, name, description, importance, confidence,
                       mastery_score, created_at, updated_at
                FROM topics
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

    public List<Topic> findByModuleId(long moduleId) throws SQLException {
        String sql = """
                SELECT id, module_id, name, description, importance, confidence,
                       mastery_score, created_at, updated_at
                FROM topics
                WHERE module_id = ?
                ORDER BY created_at DESC;
                """;

        List<Topic> topics = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, moduleId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    topics.add(mapRow(rs));
                }
            }
        }

        return topics;
    }

    public int countByModuleId(long moduleId) throws SQLException {
        String sql = "SELECT COUNT(*) AS topic_count FROM topics WHERE module_id = ?;";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, moduleId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("topic_count");
            }
        }
    }

    public double averageMasteryByModuleId(long moduleId) throws SQLException {
        String sql = """
                SELECT COALESCE(AVG(mastery_score), 0) AS average_mastery
                FROM topics
                WHERE module_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, moduleId);

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getDouble("average_mastery");
            }
        }
    }

    public void update(Topic topic) throws SQLException {
        String sql = """
                UPDATE topics
                SET name = ?,
                    description = ?,
                    importance = ?,
                    confidence = ?,
                    mastery_score = ?,
                    updated_at = ?
                WHERE id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, topic.name().trim());
            stmt.setString(2, blankToNull(topic.description()));
            stmt.setString(3, topic.importance().name());
            stmt.setString(4, topic.confidence().name());
            stmt.setDouble(5, topic.masteryScore());
            stmt.setString(6, DateUtils.toDatabaseDateTime(DateUtils.now()));
            stmt.setLong(7, topic.id());

            stmt.executeUpdate();
        }
    }

    public void deleteById(long id) throws SQLException {
        String sql = "DELETE FROM topics WHERE id = ?;";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.executeUpdate();
        }
    }

    private Topic mapRow(ResultSet rs) throws SQLException {
        return new Topic(
                rs.getLong("id"),
                rs.getLong("module_id"),
                rs.getString("name"),
                rs.getString("description"),
                ImportanceLevel.valueOf(rs.getString("importance")),
                ConfidenceLevel.valueOf(rs.getString("confidence")),
                rs.getDouble("mastery_score"),
                DateUtils.fromDatabaseDateTime(rs.getString("created_at")),
                DateUtils.fromDatabaseDateTime(rs.getString("updated_at"))
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public void updateStats(
            long topicId,
            ConfidenceLevel confidence,
            double masteryScore
    ) throws SQLException {

        String sql = """
                UPDATE topics
                SET confidence = ?,
                    mastery_score = ?,
                    updated_at = ?
                WHERE id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, confidence.name());
            stmt.setDouble(2, masteryScore);
            stmt.setString(3, DateUtils.toDatabaseDateTime(DateUtils.now()));
            stmt.setLong(4, topicId);

            stmt.executeUpdate();
        }
    }
}