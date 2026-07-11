package com.pararepilot.service;

import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.Question;
import com.pararepilot.model.Worksheet;
import com.pararepilot.repository.QuestionRepository;
import com.pararepilot.repository.WorksheetRepository;

public class WorksheetCreationService {

    private final WorksheetRepository worksheetRepository;
    private final QuestionRepository questionRepository;

    public WorksheetCreationService() {
        this(new WorksheetRepository(), new QuestionRepository());
    }

    public WorksheetCreationService(
            WorksheetRepository worksheetRepository,
            QuestionRepository questionRepository
    ) {
        this.worksheetRepository = worksheetRepository;
        this.questionRepository = questionRepository;
    }

    public Worksheet createWorksheetWithQuestions(
            long topicId,
            String title,
            String description,
            DifficultyLevel difficulty,
            ImportanceLevel importance,
            List<QuestionRepository.QuestionDraft> questionDrafts
    ) throws SQLException {

        validateWorksheet(title, questionDrafts);

        Worksheet worksheet = worksheetRepository.create(
                topicId,
                title,
                description,
                difficulty == null ? DifficultyLevel.MEDIUM : difficulty,
                importance == null ? ImportanceLevel.MEDIUM : importance
        );

        try {
            questionRepository.createMany(worksheet.id(), questionDrafts);
            return worksheet;
        } catch (SQLException e) {
            worksheetRepository.deleteById(worksheet.id());
            throw e;
        }
    }

    public List<Worksheet> getWorksheetsForTopic(long topicId) throws SQLException {
        return worksheetRepository.findByTopicId(topicId);
    }

    public List<Question> getQuestionsForWorksheet(long worksheetId) throws SQLException {
        return questionRepository.findByWorksheetId(worksheetId);
    }

    public int countWorksheetsForTopic(long topicId) throws SQLException {
        return worksheetRepository.countByTopicId(topicId);
    }

    public void deleteWorksheet(long worksheetId) throws SQLException {
        worksheetRepository.deleteById(worksheetId);
    }

    private void validateWorksheet(
            String title,
            List<QuestionRepository.QuestionDraft> questionDrafts
    ) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Worksheet title cannot be empty.");
        }

        if (title.trim().length() > 120) {
            throw new IllegalArgumentException("Worksheet title must be 120 characters or fewer.");
        }

        if (questionDrafts == null || questionDrafts.isEmpty()) {
            throw new IllegalArgumentException("A worksheet needs at least one question.");
        }

        for (int i = 0; i < questionDrafts.size(); i++) {
            QuestionRepository.QuestionDraft draft = questionDrafts.get(i);

            if (draft.prompt() == null || draft.prompt().isBlank()) {
                throw new IllegalArgumentException("Question " + (i + 1) + " needs a prompt.");
            }

            if (draft.maxMarks() <= 0) {
                throw new IllegalArgumentException("Question " + (i + 1) + " must have at least 1 mark.");
            }
        }
    }
}
