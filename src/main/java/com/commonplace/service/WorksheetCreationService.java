package com.commonplace.service;

import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.Question;
import com.commonplace.model.Worksheet;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.repository.WorksheetRepository;

import java.sql.SQLException;
import java.util.List;

public class WorksheetCreationService {

    private final WorksheetRepository worksheetRepository;
    private final QuestionRepository questionRepository;

    public WorksheetCreationService() {
        this(new WorksheetRepository(), new QuestionRepository());
    }

    public WorksheetCreationService(
            WorksheetRepository worksheetRepository, QuestionRepository questionRepository) {
        this.worksheetRepository = worksheetRepository;
        this.questionRepository = questionRepository;
    }

    public Worksheet createWorksheetWithQuestions(
            long topicId,
            String title,
            String description,
            DifficultyLevel difficulty,
            ImportanceLevel importance,
            List<QuestionRepository.QuestionDraft> questionDrafts)
            throws SQLException {

        validateWorksheet(title, questionDrafts);

        return com.commonplace.repository.DatabaseManager.transaction(
                () -> {
                    new com.commonplace.repository.LearningRepository().topic(topicId);
                    Worksheet worksheet =
                            worksheetRepository.create(
                                    topicId,
                                    title,
                                    description,
                                    difficulty == null ? DifficultyLevel.MEDIUM : difficulty,
                                    importance == null ? ImportanceLevel.MEDIUM : importance);

                    questionRepository.createMany(worksheet.id(), questionDrafts);
                    new com.commonplace.repository.LearningRepository()
                            .setScope(worksheet.id(), null, title);
                    new LearningService().refresh(topicId);
                    return worksheet;
                });
    }

    public List<Worksheet> getWorksheetsForTopic(long topicId) throws SQLException {
        return worksheetRepository.findByTopicId(topicId);
    }

    public Worksheet createWorksheetWithQuestions(
            long topicId,
            String title,
            String description,
            DifficultyLevel difficulty,
            ImportanceLevel importance,
            List<QuestionRepository.QuestionDraft> drafts,
            String subject,
            String scope)
            throws SQLException {
        return com.commonplace.repository.DatabaseManager.transaction(
                () -> {
                    Worksheet worksheet =
                            createWorksheetWithQuestions(
                                    topicId, title, description, difficulty, importance, drafts);
                    new com.commonplace.repository.LearningRepository()
                            .setScope(worksheet.id(), subject, scope);
                    return worksheet;
                });
    }

    public List<Question> getQuestionsForWorksheet(long worksheetId) throws SQLException {
        return questionRepository.findByWorksheetId(worksheetId);
    }

    public int countWorksheetsForTopic(long topicId) throws SQLException {
        return worksheetRepository.countByTopicId(topicId);
    }

    public void deleteWorksheet(long worksheetId) throws SQLException {
        com.commonplace.repository.DatabaseManager.transaction(
                () -> {
                    Worksheet worksheet =
                            worksheetRepository
                                    .findById(worksheetId)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Worksheet is not available in this"
                                                                + " account."));
                    worksheetRepository.deleteById(worksheetId);
                    new LearningService().refresh(worksheet.topicId());
                    return null;
                });
    }

    private void validateWorksheet(
            String title, List<QuestionRepository.QuestionDraft> questionDrafts) {
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
                throw new IllegalArgumentException(
                        "Question " + (i + 1) + " must have at least 1 mark.");
            }
        }
    }
}
