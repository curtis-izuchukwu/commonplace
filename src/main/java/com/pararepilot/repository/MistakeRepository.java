package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import com.pararepilot.model.MistakeBankItem;
import com.pararepilot.service.AccountSession;
import com.pararepilot.util.DateUtils;

public class MistakeRepository {

    public int createFromAttempt(long attemptId) throws SQLException {
        String sql = """
                INSERT INTO mistake_bank
                    (topic_id, worksheet_id, question_id, attempt_id,
                     user_answer, mark_scheme, mistake_note, created_at,
                     resolved, times_revisited)
                SELECT
                    w.topic_id,
                    w.id,
                    q.id,
                    a.attempt_id,
                    a.user_answer,
                    q.mark_scheme,
                    a.mistake_note,
                    ?,
                    0,
                    0
                FROM answers a
                JOIN questions q ON q.id = a.question_id
                JOIN worksheets w ON w.id = q.worksheet_id
                WHERE a.attempt_id = ?
                  AND a.marked_as_mistake = 1
                  AND NOT EXISTS (
                      SELECT 1
                      FROM mistake_bank mb
                      WHERE mb.attempt_id = a.attempt_id
                        AND mb.question_id = a.question_id
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, DateUtils.toDatabaseDateTime(DateUtils.now()));
            stmt.setLong(2, attemptId);

            return stmt.executeUpdate();
        }
    }

    public List<MistakeBankItem> findAll() throws SQLException {
        String sql = """
                SELECT id, topic_id, worksheet_id, question_id, attempt_id,
                       user_answer, mark_scheme, mistake_note, created_at,
                       resolved, times_revisited
                FROM mistake_bank
                WHERE EXISTS (
                    SELECT 1
                    FROM modules m
                    JOIN topics t ON t.module_id = m.id
                    WHERE t.id = mistake_bank.topic_id
                      AND m.user_id = ?
                )
                ORDER BY resolved ASC, created_at DESC;
                """;

        List<MistakeBankItem> mistakes = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    mistakes.add(mapRow(rs));
                }
            }
        }

        return mistakes;
    }

    public List<MistakeBankItem> findByTopicId(long topicId) throws SQLException {
        String sql = """
                SELECT id, topic_id, worksheet_id, question_id, attempt_id,
                       user_answer, mark_scheme, mistake_note, created_at,
                       resolved, times_revisited
                FROM mistake_bank
                WHERE topic_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM modules m
                      JOIN topics t ON t.module_id = m.id
                      WHERE t.id = mistake_bank.topic_id
                        AND m.user_id = ?
                  )
                ORDER BY resolved ASC, created_at DESC;
                """;

        List<MistakeBankItem> mistakes = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, topicId);
            stmt.setLong(2, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    mistakes.add(mapRow(rs));
                }
            }
        }

        return mistakes;
    }

    public List<MistakeDisplayItem> findDisplayItems() throws SQLException {
        String sql = """
                SELECT
                    mb.id,
                    mb.topic_id,
                    mb.worksheet_id,
                    mb.question_id,
                    mb.attempt_id,
                    t.name AS topic_name,
                    w.title AS worksheet_title,
                    q.prompt AS question_prompt,
                    mb.user_answer,
                    mb.mark_scheme,
                    mb.mistake_note,
                    mb.created_at,
                    mb.resolved,
                    mb.times_revisited
                FROM mistake_bank mb
                JOIN topics t ON t.id = mb.topic_id
                JOIN worksheets w ON w.id = mb.worksheet_id
                JOIN questions q ON q.id = mb.question_id
                JOIN modules m ON m.id = t.module_id
                WHERE m.user_id = ?
                ORDER BY mb.resolved ASC, mb.created_at DESC;
                """;

        List<MistakeDisplayItem> mistakes = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    mistakes.add(mapDisplayRow(rs));
                }
            }
        }

        return mistakes;
    }

    public List<MistakeDisplayItem> findDisplayItemsByTopicId(long topicId) throws SQLException {
        String sql = """
                SELECT
                    mb.id,
                    mb.topic_id,
                    mb.worksheet_id,
                    mb.question_id,
                    mb.attempt_id,
                    t.name AS topic_name,
                    w.title AS worksheet_title,
                    q.prompt AS question_prompt,
                    mb.user_answer,
                    mb.mark_scheme,
                    mb.mistake_note,
                    mb.created_at,
                    mb.resolved,
                    mb.times_revisited
                FROM mistake_bank mb
                JOIN topics t ON t.id = mb.topic_id
                JOIN worksheets w ON w.id = mb.worksheet_id
                JOIN questions q ON q.id = mb.question_id
                JOIN modules m ON m.id = t.module_id
                WHERE mb.topic_id = ?
                  AND m.user_id = ?
                ORDER BY mb.resolved ASC, mb.created_at DESC;
                """;

        List<MistakeDisplayItem> mistakes = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, topicId);
            stmt.setLong(2, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    mistakes.add(mapDisplayRow(rs));
                }
            }
        }

        return mistakes;
    }

    public int countUnresolvedByWorksheetId(long worksheetId) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS unresolved_count
                FROM mistake_bank
                WHERE worksheet_id = ?
                  AND resolved = 0
                  AND EXISTS (
                      SELECT 1
                      FROM modules m
                      JOIN topics t ON t.module_id = m.id
                      WHERE t.id = mistake_bank.topic_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, worksheetId);
            stmt.setLong(2, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("unresolved_count");
            }
        }
    }

    public int countUnresolvedByTopicId(long topicId) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS unresolved_count
                FROM mistake_bank
                WHERE topic_id = ?
                  AND resolved = 0
                  AND EXISTS (
                      SELECT 1
                      FROM modules m
                      JOIN topics t ON t.module_id = m.id
                      WHERE t.id = mistake_bank.topic_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, topicId);
            stmt.setLong(2, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("unresolved_count");
            }
        }
    }

    public int countUnresolved() throws SQLException {
        String sql = """
                SELECT COUNT(*) AS unresolved_count
                FROM mistake_bank
                WHERE resolved = 0
                  AND EXISTS (
                      SELECT 1
                      FROM modules m
                      JOIN topics t ON t.module_id = m.id
                      WHERE t.id = mistake_bank.topic_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("unresolved_count");
            }
        }
    }

    public int countByWorksheetId(long worksheetId) throws SQLException {
        String sql = """
                SELECT COUNT(*) AS mistake_count
                FROM mistake_bank
                WHERE worksheet_id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM modules m
                      JOIN topics t ON t.module_id = m.id
                      WHERE t.id = mistake_bank.topic_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, worksheetId);
            stmt.setLong(2, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                return rs.getInt("mistake_count");
            }
        }
    }

    public void markResolved(long mistakeId, boolean resolved) throws SQLException {
        String sql = """
                UPDATE mistake_bank
                SET resolved = ?
                WHERE id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM modules m
                      JOIN topics t ON t.module_id = m.id
                      WHERE t.id = mistake_bank.topic_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, resolved ? 1 : 0);
            stmt.setLong(2, mistakeId);
            stmt.setLong(3, AccountSession.currentUserId());

            stmt.executeUpdate();
        }
    }

    public void incrementRevisitCount(long mistakeId) throws SQLException {
        String sql = """
                UPDATE mistake_bank
                SET times_revisited = times_revisited + 1
                WHERE id = ?
                  AND EXISTS (
                      SELECT 1
                      FROM modules m
                      JOIN topics t ON t.module_id = m.id
                      WHERE t.id = mistake_bank.topic_id
                        AND m.user_id = ?
                  );
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, mistakeId);
            stmt.setLong(2, AccountSession.currentUserId());
            stmt.executeUpdate();
        }
    }

    private MistakeBankItem mapRow(ResultSet rs) throws SQLException {
        return new MistakeBankItem(
                rs.getLong("id"),
                rs.getLong("topic_id"),
                rs.getLong("worksheet_id"),
                rs.getLong("question_id"),
                rs.getLong("attempt_id"),
                rs.getString("user_answer"),
                rs.getString("mark_scheme"),
                rs.getString("mistake_note"),
                DateUtils.fromDatabaseDateTime(rs.getString("created_at")),
                rs.getInt("resolved") == 1,
                rs.getInt("times_revisited")
        );
    }

    private MistakeDisplayItem mapDisplayRow(ResultSet rs) throws SQLException {
        return new MistakeDisplayItem(
                rs.getLong("id"),
                rs.getLong("topic_id"),
                rs.getLong("worksheet_id"),
                rs.getLong("question_id"),
                rs.getLong("attempt_id"),
                rs.getString("topic_name"),
                rs.getString("worksheet_title"),
                rs.getString("question_prompt"),
                rs.getString("user_answer"),
                rs.getString("mark_scheme"),
                rs.getString("mistake_note"),
                DateUtils.fromDatabaseDateTime(rs.getString("created_at")),
                rs.getInt("resolved") == 1,
                rs.getInt("times_revisited")
        );
    }

    public record MistakeDisplayItem(
            long id,
            long topicId,
            long worksheetId,
            long questionId,
            long attemptId,
            String topicName,
            String worksheetTitle,
            String questionPrompt,
            String userAnswer,
            String markScheme,
            String mistakeNote,
            java.time.LocalDateTime createdAt,
            boolean resolved,
            int timesRevisited
    ) {
    }
}
