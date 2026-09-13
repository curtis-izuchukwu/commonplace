package com.commonplace.repository;

import com.commonplace.model.Answer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AnswerRepository {

    public void createMany(long attemptId, List<AnswerDraft> answerDrafts) throws SQLException {
        String sql =
                """
INSERT INTO answers
    (attempt_id, question_id, user_answer, awarded_marks, max_marks,
     marked_as_mistake, mistake_note, assisted, active_seconds, evidence_mode, initial_answer)
VALUES
    (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
""";

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (AnswerDraft draft : answerDrafts) {
                stmt.setLong(1, attemptId);
                stmt.setLong(2, draft.questionId());
                stmt.setString(3, draft.userAnswer().trim());
                stmt.setInt(4, draft.awardedMarks());
                stmt.setInt(5, draft.maxMarks());
                stmt.setInt(6, draft.markedAsMistake() ? 1 : 0);
                stmt.setString(7, blankToNull(draft.mistakeNote()));
                stmt.setBoolean(8, draft.assisted());
                stmt.setInt(9, Math.max(0, draft.activeSeconds()));
                stmt.setString(10, draft.locked() ? "LOCKED_SELF" : "SELF");
                stmt.setString(11, draft.userAnswer().trim());

                stmt.addBatch();
            }

            stmt.executeBatch();
        }
    }

    public List<Answer> findByAttemptId(long attemptId) throws SQLException {
        String sql =
                """
                SELECT id, attempt_id, question_id, user_answer, awarded_marks,
                       max_marks, marked_as_mistake, mistake_note
                FROM answers
                WHERE attempt_id = ?
                ORDER BY id ASC;
                """;

        List<Answer> answers = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
                PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, attemptId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    answers.add(mapRow(rs));
                }
            }
        }

        return answers;
    }

    private Answer mapRow(ResultSet rs) throws SQLException {
        return new Answer(
                rs.getLong("id"),
                rs.getLong("attempt_id"),
                rs.getLong("question_id"),
                rs.getString("user_answer"),
                rs.getInt("awarded_marks"),
                rs.getInt("max_marks"),
                rs.getInt("marked_as_mistake") == 1,
                rs.getString("mistake_note"));
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public record AnswerDraft(
            long questionId,
            String userAnswer,
            int awardedMarks,
            int maxMarks,
            boolean markedAsMistake,
            String mistakeNote,
            boolean assisted,
            int activeSeconds,
            boolean locked) {
        public AnswerDraft(
                long questionId,
                String userAnswer,
                int awardedMarks,
                int maxMarks,
                boolean markedAsMistake,
                String mistakeNote) {
            this(
                    questionId,
                    userAnswer,
                    awardedMarks,
                    maxMarks,
                    markedAsMistake,
                    mistakeNote,
                    false,
                    0,
                    false);
        }
    }
}
