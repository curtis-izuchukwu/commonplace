package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.service.AccountSession;
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
                WHERE id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM worksheets w
                      JOIN topics t ON t.id = w.topic_id
                      JOIN modules m ON m.id = t.module_id
                      WHERE w.id = worksheet_attempts.worksheet_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, id);
            stmt.setLong(2, AccountSession.currentUserId());

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
                  AND EXISTS (
                      SELECT 1
                      FROM worksheets w
                      JOIN topics t ON t.id = w.topic_id
                      JOIN modules m ON m.id = t.module_id
                      WHERE w.id = worksheet_attempts.worksheet_id
                        AND m.user_id = ?
                  )
                ORDER BY completed_at DESC;
                """;

        List<WorksheetAttempt> attempts = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, worksheetId);
            stmt.setLong(2, AccountSession.currentUserId());

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
    public void updateReflection(
            long attemptId,
            ConfidenceLevel confidenceAfter,
            String mainWeakness,
            String nextAction,
            String reflectionNotes
    ) throws SQLException {

        String sql = """
                UPDATE worksheet_attempts
                SET confidence_after = ?,
                    main_weakness = ?,
                    next_action = ?,
                    reflection_notes = ?
                WHERE id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM worksheets w
                      JOIN topics t ON t.id = w.topic_id
                      JOIN modules m ON m.id = t.module_id
                      WHERE w.id = worksheet_attempts.worksheet_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, confidenceAfter.name());
            stmt.setString(2, blankToNull(mainWeakness));
            stmt.setString(3, blankToNull(nextAction));
            stmt.setString(4, blankToNull(reflectionNotes));
            stmt.setLong(5, attemptId);
            stmt.setLong(6, AccountSession.currentUserId());

            stmt.executeUpdate();
        }
    }

    public boolean markXpAwardedIfPending(long attemptId, LocalDateTime awardedAt)
            throws SQLException {

        String sql = """
                UPDATE worksheet_attempts
                SET xp_awarded_at = ?
                WHERE id = ?
                  AND xp_awarded_at IS NULL
                  AND EXISTS (
                      SELECT 1
                      FROM worksheets w
                      JOIN topics t ON t.id = w.topic_id
                      JOIN modules m ON m.id = t.module_id
                      WHERE w.id = worksheet_attempts.worksheet_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, DateUtils.toDatabaseDateTime(awardedAt));
            stmt.setLong(2, attemptId);
            stmt.setLong(3, AccountSession.currentUserId());

            return stmt.executeUpdate() > 0;
        }
    }

    public List<WorksheetAttempt> findRecentByWorksheetIds(List<Long> worksheetIds, int limit)
            throws SQLException {

        if (worksheetIds == null || worksheetIds.isEmpty()) {
            return List.of();
        }

        String placeholders = String.join(",", worksheetIds.stream().map(id -> "?").toList());

        String sql = """
                SELECT id, worksheet_id, started_at, completed_at, score, max_score,
                    score_percent, confidence_after, main_weakness, next_action, reflection_notes
                FROM worksheet_attempts
                WHERE worksheet_id IN (%s)
                  AND EXISTS (
                      SELECT 1
                      FROM worksheets w
                      JOIN topics t ON t.id = w.topic_id
                      JOIN modules m ON m.id = t.module_id
                      WHERE w.id = worksheet_attempts.worksheet_id
                        AND m.user_id = ?
                  )
                ORDER BY completed_at DESC
                LIMIT ?;
                """.formatted(placeholders);

        List<WorksheetAttempt> attempts = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            int index = 1;

            for (Long worksheetId : worksheetIds) {
                stmt.setLong(index++, worksheetId);
            }

            stmt.setLong(index++, AccountSession.currentUserId());
            stmt.setInt(index, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    attempts.add(mapRow(rs));
                }
            }
        }

        return attempts;
    }

    public List<RecentAttemptDisplayItem> findRecentDisplayItems(int limit) throws SQLException {
        String sql = """
                SELECT
                    wa.id AS attempt_id,
                    wa.worksheet_id,
                    w.topic_id,
                    w.title AS worksheet_title,
                    t.name AS topic_name,
                    wa.completed_at,
                    wa.score,
                    wa.max_score,
                    wa.score_percent,
                    wa.confidence_after
                FROM worksheet_attempts wa
                JOIN worksheets w ON w.id = wa.worksheet_id
                JOIN topics t ON t.id = w.topic_id
                JOIN modules m ON m.id = t.module_id
                WHERE m.user_id = ?
                ORDER BY wa.completed_at DESC
                LIMIT ?;
                """;

        List<RecentAttemptDisplayItem> attempts = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setInt(2, limit);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    attempts.add(new RecentAttemptDisplayItem(
                            rs.getLong("attempt_id"),
                            rs.getLong("worksheet_id"),
                            rs.getLong("topic_id"),
                            rs.getString("worksheet_title"),
                            rs.getString("topic_name"),
                            DateUtils.fromDatabaseDateTime(rs.getString("completed_at")),
                            rs.getInt("score"),
                            rs.getInt("max_score"),
                            rs.getDouble("score_percent"),
                            ConfidenceLevel.valueOf(rs.getString("confidence_after"))
                    ));
                }
            }
        }

        return attempts;
    }

    public int countCompletedOn(LocalDate completionDate) throws SQLException {
        LocalDateTime startOfDay = completionDate.atStartOfDay();
        LocalDateTime startOfNextDay = completionDate.plusDays(1).atStartOfDay();

        String sql = """
                SELECT COUNT(*) AS attempt_count
                FROM worksheet_attempts wa
                JOIN worksheets w ON w.id = wa.worksheet_id
                JOIN topics t ON t.id = w.topic_id
                JOIN modules m ON m.id = t.module_id
                WHERE m.user_id = ?
                  AND wa.completed_at >= ?
                  AND wa.completed_at < ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setString(2, DateUtils.toDatabaseDateTime(startOfDay));
            stmt.setString(3, DateUtils.toDatabaseDateTime(startOfNextDay));

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("attempt_count");
            }
        }
    }

    public Set<Long> findWorksheetIdsCompletedOn(LocalDate completionDate) throws SQLException {
        LocalDateTime startOfDay = completionDate.atStartOfDay();
        LocalDateTime startOfNextDay = completionDate.plusDays(1).atStartOfDay();

        String sql = """
                SELECT DISTINCT wa.worksheet_id
                FROM worksheet_attempts wa
                JOIN worksheets w ON w.id = wa.worksheet_id
                JOIN topics t ON t.id = w.topic_id
                JOIN modules m ON m.id = t.module_id
                WHERE m.user_id = ?
                  AND wa.completed_at >= ?
                  AND wa.completed_at < ?;
                """;

        Set<Long> worksheetIds = new HashSet<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setString(2, DateUtils.toDatabaseDateTime(startOfDay));
            stmt.setString(3, DateUtils.toDatabaseDateTime(startOfNextDay));

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    worksheetIds.add(rs.getLong("worksheet_id"));
                }
            }
        }

        return worksheetIds;
    }
    
    public record RecentAttemptDisplayItem(
            long attemptId,
            long worksheetId,
            long topicId,
            String worksheetTitle,
            String topicName,
            java.time.LocalDateTime completedAt,
            int score,
            int maxScore,
            double scorePercent,
            ConfidenceLevel confidenceAfter
    ) {
    }
}
