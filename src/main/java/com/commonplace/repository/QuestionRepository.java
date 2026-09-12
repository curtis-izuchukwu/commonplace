package com.commonplace.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import com.commonplace.model.Question;

public class QuestionRepository {

    public Question create(
            long worksheetId,
            String prompt,
            String markScheme,
            int maxMarks,
            int questionOrder,
            String tags
    ) throws SQLException {

        return create(
                worksheetId,
                prompt,
                markScheme,
                maxMarks,
                questionOrder,
                tags,
                null
        );
    }

    public Question create(
            long worksheetId,
            String prompt,
            String markScheme,
            int maxMarks,
            int questionOrder,
            String tags,
            String imagePath
    ) throws SQLException {

        String sql = """
                INSERT INTO questions
                    (worksheet_id, prompt, mark_scheme, max_marks, question_order, tags, image_path)
                VALUES
                    (?, ?, ?, ?, ?, ?, ?);
                """;

        try (Connection conn = DatabaseManager.connect();
            PreparedStatement stmt = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            String normalizedMarkScheme = markScheme == null ? "" : markScheme.trim();

            stmt.setLong(1, worksheetId);
            stmt.setString(2, prompt.trim());
            stmt.setString(3, normalizedMarkScheme);
            stmt.setInt(4, maxMarks);
            stmt.setInt(5, questionOrder);
            stmt.setString(6, blankToNull(tags));
            stmt.setString(7, blankToNull(imagePath));

            stmt.executeUpdate();

            try (ResultSet keys = stmt.getGeneratedKeys()) {
                if (keys.next()) {
                    return new Question(
                            keys.getLong(1),
                            worksheetId,
                            prompt.trim(),
                            normalizedMarkScheme,
                            maxMarks,
                            questionOrder,
                            blankToNull(tags),
                            blankToNull(imagePath)
                    );
                }
            }

            throw new SQLException("Creating question failed; no ID returned.");
        }
    }

    public List<Question> createMany(long worksheetId, List<QuestionDraft> drafts) throws SQLException {
        List<Question> createdQuestions = new ArrayList<>();

        for (int i = 0; i < drafts.size(); i++) {
            QuestionDraft draft = drafts.get(i);

            createdQuestions.add(create(
                    worksheetId,
                    draft.prompt(),
                    draft.markScheme(),
                    draft.maxMarks(),
                    i + 1,
                    draft.tags(),
                    draft.imagePath()
            ));
        }

        return createdQuestions;
    }

    public List<Question> findByWorksheetId(long worksheetId) throws SQLException {
        String sql = """
                SELECT id, worksheet_id, prompt, mark_scheme, max_marks, question_order, tags, image_path
                FROM questions
                WHERE worksheet_id = ?
                ORDER BY question_order ASC;
                """;

        List<Question> questions = new ArrayList<>();

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, worksheetId);

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    questions.add(mapRow(rs));
                }
            }
        }

        return questions;
    }

    public void deleteByWorksheetId(long worksheetId) throws SQLException {
        String sql = "DELETE FROM questions WHERE worksheet_id = ?;";

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, worksheetId);
            stmt.executeUpdate();
        }
    }

    private Question mapRow(ResultSet rs) throws SQLException {
        return new Question(
                rs.getLong("id"),
                rs.getLong("worksheet_id"),
                rs.getString("prompt"),
                rs.getString("mark_scheme"),
                rs.getInt("max_marks"),
                rs.getInt("question_order"),
                rs.getString("tags"),
                rs.getString("image_path")
        );
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    public record QuestionDraft(
            String prompt,
            String markScheme,
            int maxMarks,
            String tags,
            String imagePath
    ) {
        public QuestionDraft(
                String prompt,
                String markScheme,
                int maxMarks,
                String tags
        ) {
            this(prompt, markScheme, maxMarks, tags, null);
        }
    }
}
