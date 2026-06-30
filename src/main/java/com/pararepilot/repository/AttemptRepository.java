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
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.util.DateUtils;

public class AttemptRepository {

    public WorksheetAttempt create(
            long worksheetId,
            LocalDateTime startedAt,
            LocalDateTime completedAt,
            int score,
            int maxScore,
            double scorePercent,
            ConfidenceLevel confidenceAfter,
            String mainWeakness,
            String nextAction,
            String reflectionNotes
    ) throws SQLException {

        String sql = """
                INSERT INTO worksheet_attempts
                    (worksheet_id, started_at, completed_at, score, max_score,
                     score_percent, confidence_after, main_weakness, next_action, reflection_notes)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            stmt.setLong(1, worksheetId);
            stmt.setString(2, DateUtils.toDatabaseDateTime(startedAt));
            stmt.setString(3, DateUtils.toDatabaseDateTime(completedAt));
            stmt.setInt(4, score);
            stmt.setInt(5, maxScore);
            stmt.setDouble(6, scorePercent);
            stmt.setString(7, confidenceAfter.name());
            stmt.setString(8, blankToNull(mainWeakness));
            stmt.setString(9, blankToNull(nextAction));
            stmt.setString(10, blankToNull(reflectionNotes));

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return findById(keys.getLong(1))
                            .orElseThrow(() -> new SQLException("Created attempt could not be reloaded."));
                }
            }

            throw new SQLException("Creating attempt failed; no ID returned.");
        }
    }

    public Optional<WorksheetAttempt> findById(long id) throws SQLException {
        String sql = """
                SELECT id, worksheet_id, started_at, completed_at, score, max_score,
                       score_percent, confidence_after, main_weakness, next_action, reflection_notes
                FROM worksheet_attempts
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

    public List<WorksheetAttempt> findByWorksheetId(long worksheetId) throws SQLException {
        String sql = """
                SELECT id, worksheet_id, started_at, completed_at, score, max_score,
                       score_percent, confidence_after, main_weakness, next_action, reflection_notes
                FROM worksheet_attempts
                WHERE worksheet_id = ?
                ORDER BY completed_at DESC;
                """;

        List<WorksheetAttempt> attempts = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, worksheetId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    attempts.add(mapRow(rs));
                }
            }
        }

        return attempts;
    }

    private WorksheetAttempt mapRow(ResultSet rs) throws SQLException {
        return new WorksheetAttempt(
                rs.getLong("id"),
                rs.getLong("worksheet_id"),
                DateUtils.fromDatabaseDateTime(rs.getString("started_at")),
                DateUtils.fromDatabaseDateTime(rs.getString("completed_at")),
                rs.getInt("score"),
                rs.getInt("max_score"),
                rs.getDouble("score_percent"),
                ConfidenceLevel.valueOf(rs.getString("confidence_after")),
                rs.getString("main_weakness"),
                rs.getString("next_action"),
                rs.getString("reflection_notes")
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }
}