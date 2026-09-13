package com.commonplace.repository;

import com.commonplace.model.DifficultyLevel;
import com.commonplace.service.AccountSession;
import com.commonplace.service.LearningModel;
import com.commonplace.util.DateUtils;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Persistence for automatic question-level learning evidence. */
public class LearningRepository {

    public record TopicInfo(
            long id, long moduleId, String name, String importance, LocalDate examDate) {}

    public TopicInfo topic(long topicId) throws SQLException {
        String sql =
                """
                SELECT t.id, t.module_id, t.name, t.importance, m.exam_date
                FROM topics t
                JOIN modules m ON m.id = t.module_id
                WHERE t.id = ? AND m.user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, topicId);
            stmt.setLong(2, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Topic is not available in this account.");
                }
                return new TopicInfo(
                        rs.getLong("id"),
                        rs.getLong("module_id"),
                        rs.getString("name"),
                        rs.getString("importance"),
                        DateUtils.fromDatabaseDate(rs.getString("exam_date")));
            }
        }
    }

    public List<Long> topicIds() throws SQLException {
        List<Long> ids = new ArrayList<>();
        String sql =
                """
                SELECT t.id
                FROM topics t
                JOIN modules m ON m.id = t.module_id
                WHERE m.user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, AccountSession.currentUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    ids.add(rs.getLong(1));
                }
            }
        }
        return ids;
    }

    public int topicQuestionCount(long topicId) throws SQLException {
        topic(topicId);
        String sql =
                """
                SELECT COUNT(*)
                FROM questions q
                JOIN worksheets w ON w.id = q.worksheet_id
                WHERE w.topic_id = ?;
                """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, topicId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt(1) : 0;
            }
        }
    }

    public List<LearningModel.Evidence> topicEvidence(long topicId) throws SQLException {
        topic(topicId);
        return evidence("w.topic_id = ?", topicId);
    }

    public List<LearningModel.Evidence> worksheetEvidence(long worksheetId) throws SQLException {
        requireWorksheet(worksheetId);
        return evidence("w.id = ?", worksheetId);
    }

    private List<LearningModel.Evidence> evidence(String filter, long id) throws SQLException {
        List<LearningModel.Evidence> result = new ArrayList<>();
        String answerSql =
                """
                SELECT a.*, wa.worksheet_id, wa.completed_at, q.prompt,
                       COALESCE(q.assessed_difficulty, w.difficulty) AS difficulty,
                       COALESCE(NULLIF(w.study_scope, ''), w.title) AS study_scope
                FROM answers a
                JOIN worksheet_attempts wa ON wa.id = a.attempt_id
                JOIN questions q ON q.id = a.question_id
                JOIN worksheets w ON w.id = q.worksheet_id
                JOIN topics t ON t.id = w.topic_id
                JOIN modules m ON m.id = t.module_id
                WHERE %s AND m.user_id = ?
                ORDER BY wa.completed_at, wa.id, a.id;
                """
                        .formatted(filter);

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(answerSql)) {
            stmt.setLong(1, id);
            stmt.setLong(2, AccountSession.currentUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    result.add(
                            new LearningModel.Evidence(
                                    rs.getLong("attempt_id"),
                                    rs.getLong("worksheet_id"),
                                    LearningModel.fingerprint(rs.getString("prompt")),
                                    DateUtils.fromDatabaseDateTime(rs.getString("completed_at")),
                                    rs.getDouble("awarded_marks"),
                                    rs.getDouble("max_marks"),
                                    rs.getString("difficulty"),
                                    rs.getBoolean("assisted"),
                                    rs.getBoolean("marked_as_mistake"),
                                    rs.getString("evidence_mode"),
                                    rs.getInt("active_seconds"),
                                    rs.getString("study_scope")));
                }
            }
        }

        String reviewSql =
                """
                SELECT r.*, q.prompt, q.max_marks, w.id AS worksheet_id,
                       COALESCE(q.assessed_difficulty, w.difficulty) AS difficulty,
                       COALESCE(NULLIF(w.study_scope, ''), w.title) AS study_scope
                FROM mistake_reviews r
                JOIN mistake_bank mb ON mb.id = r.mistake_id
                JOIN questions q ON q.id = mb.question_id
                JOIN worksheets w ON w.id = q.worksheet_id
                JOIN topics t ON t.id = w.topic_id
                JOIN modules m ON m.id = t.module_id
                WHERE %s AND m.user_id = ?
                ORDER BY r.reviewed_at, r.id;
                """
                        .formatted(filter);

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(reviewSql)) {
            stmt.setLong(1, id);
            stmt.setLong(2, AccountSession.currentUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    double marks = Math.min(5, rs.getInt("max_marks"));
                    result.add(
                            new LearningModel.Evidence(
                                    -rs.getLong("id"),
                                    rs.getLong("worksheet_id"),
                                    LearningModel.fingerprint(rs.getString("prompt")),
                                    DateUtils.fromDatabaseDateTime(rs.getString("reviewed_at")),
                                    rs.getBoolean("success") ? marks : 0,
                                    marks,
                                    rs.getString("difficulty"),
                                    rs.getBoolean("assisted"),
                                    !rs.getBoolean("success"),
                                    "LOCKED_SELF",
                                    0,
                                    rs.getString("study_scope")));
                }
            }
        }
        return result;
    }

    public double mistakeRiskForWorksheet(long worksheetId, boolean includeResolved)
            throws SQLException {
        requireWorksheet(worksheetId);
        Map<String, Double> risk = new HashMap<>();
        Map<String, Integer> recurrence = new HashMap<>();
        String sql =
                """
                SELECT q.prompt, mb.resolved, mb.created_at, a.awarded_marks, a.max_marks
                FROM mistake_bank mb
                JOIN questions q ON q.id = mb.question_id
                JOIN worksheets w ON w.id = q.worksheet_id
                JOIN topics t ON t.id = w.topic_id
                JOIN modules m ON m.id = t.module_id
                JOIN answers a ON a.attempt_id = mb.attempt_id AND a.question_id = mb.question_id
                WHERE w.id = ? AND m.user_id = ? AND (mb.resolved = 0 OR ? = 1)
                ORDER BY mb.created_at;
                """;

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, worksheetId);
            stmt.setLong(2, AccountSession.currentUserId());
            stmt.setBoolean(3, includeResolved);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String fingerprint = LearningModel.fingerprint(rs.getString("prompt"));
                    int repeats = recurrence.merge(fingerprint, 1, Integer::sum);
                    long age =
                            Math.max(
                                    0,
                                    java.time.temporal.ChronoUnit.DAYS.between(
                                            DateUtils.fromDatabaseDateTime(
                                                            rs.getString("created_at"))
                                                    .toLocalDate(),
                                            LocalDate.now()));
                    double severity =
                            Math.max(
                                    .2,
                                    1
                                            - rs.getDouble("awarded_marks")
                                                    / Math.max(1, rs.getInt("max_marks")));
                    double value =
                            severity
                                    * Math.min(1, rs.getInt("max_marks") / 5.0)
                                    * (1 + .1 * Math.log1p(repeats))
                                    * Math.exp(-age / 45.0)
                                    * (rs.getBoolean("resolved") ? .15 : 1);
                    risk.put(fingerprint, LearningModel.clamp(value));
                }
            }
        }
        return risk.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
    }

    public void setScope(long worksheetId, String subject, String scope) throws SQLException {
        String sql =
                """
                UPDATE worksheets
                SET generation_subject = ?, study_scope = ?
                WHERE id = ? AND topic_id IN (
                    SELECT t.id FROM topics t JOIN modules m ON m.id = t.module_id
                    WHERE m.user_id = ?
                );
                """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, blankToNull(subject));
            stmt.setString(2, blankToNull(scope));
            stmt.setLong(3, worksheetId);
            stmt.setLong(4, AccountSession.currentUserId());
            if (stmt.executeUpdate() == 0) {
                throw new IllegalArgumentException("Worksheet is not available.");
            }
        }
    }

    public String worksheetScope(long worksheetId) throws SQLException {
        String sql =
                """
                SELECT COALESCE(NULLIF(w.study_scope, ''), w.title)
                FROM worksheets w
                JOIN topics t ON t.id = w.topic_id
                JOIN modules m ON m.id = t.module_id
                WHERE w.id = ? AND m.user_id = ?;
                """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setLong(1, worksheetId);
            stmt.setLong(2, AccountSession.currentUserId());
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalArgumentException("Worksheet is not available.");
                }
                return rs.getString(1);
            }
        }
    }

    public void setQuestionDifficulty(long questionId, DifficultyLevel difficulty)
            throws SQLException {
        String sql =
                """
                UPDATE questions
                SET assessed_difficulty = ?
                WHERE id = ? AND worksheet_id IN (
                    SELECT w.id FROM worksheets w
                    JOIN topics t ON t.id = w.topic_id
                    JOIN modules m ON m.id = t.module_id
                    WHERE m.user_id = ?
                );
                """;
        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, difficulty.name());
            stmt.setLong(2, questionId);
            stmt.setLong(3, AccountSession.currentUserId());
            if (stmt.executeUpdate() == 0) {
                throw new IllegalArgumentException("Question is not available.");
            }
        }
    }

    private void requireWorksheet(long worksheetId) throws SQLException {
        worksheetScope(worksheetId);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
