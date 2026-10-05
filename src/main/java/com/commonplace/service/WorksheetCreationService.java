package com.commonplace.service;

import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.Question;
import com.commonplace.model.Worksheet;
import com.commonplace.repository.QuestionRepository;
import com.commonplace.repository.WorksheetRepository;

import java.sql.SQLException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public class WorksheetCreationService {

    private final WorksheetRepository worksheetRepository;
    private final QuestionRepository questionRepository;
    private final PdfWorksheetStorage pdfStorage;

    public WorksheetCreationService() {
        this(new WorksheetRepository(), new QuestionRepository(), new PdfWorksheetStorage());
    }

    public WorksheetCreationService(
            WorksheetRepository worksheetRepository, QuestionRepository questionRepository) {
        this(worksheetRepository, questionRepository, new PdfWorksheetStorage());
    }

    WorksheetCreationService(
            WorksheetRepository worksheetRepository,
            QuestionRepository questionRepository,
            PdfWorksheetStorage pdfStorage) {
        this.worksheetRepository = worksheetRepository;
        this.questionRepository = questionRepository;
        this.pdfStorage = pdfStorage;
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

    public Optional<Worksheet> getWorksheet(long worksheetId) throws SQLException {
        return worksheetRepository.findById(worksheetId);
    }

    public Worksheet createPdfWorksheet(
            long topicId,
            String title,
            String description,
            DifficultyLevel difficulty,
            ImportanceLevel importance,
            Path sourcePdf,
            Path markSchemePdf)
            throws SQLException, IOException {
        validateTitle(title);
        String storedPath = pdfStorage.copyIntoPdfStore(sourcePdf);
        String storedMarkSchemePath = null;

        try {
            if (markSchemePdf != null) {
                storedMarkSchemePath = pdfStorage.copyIntoPdfStore(markSchemePdf);
            }
        } catch (IOException | RuntimeException exception) {
            pdfStorage.deleteStoredPdf(storedPath);
            throw exception;
        }

        String finalMarkSchemePath = storedMarkSchemePath;

        try {
            return com.commonplace.repository.DatabaseManager.transaction(
                    () -> {
                        new com.commonplace.repository.LearningRepository().topic(topicId);
                        Worksheet worksheet =
                                worksheetRepository.createPdf(
                                        topicId,
                                        title,
                                        description,
                                        difficulty == null ? DifficultyLevel.MEDIUM : difficulty,
                                        importance == null ? ImportanceLevel.MEDIUM : importance,
                                        storedPath,
                                        finalMarkSchemePath);
                        new com.commonplace.repository.LearningRepository()
                                .setScope(worksheet.id(), null, title);
                        new LearningService().refresh(topicId);
                        return worksheet;
                    });
        } catch (SQLException | RuntimeException exception) {
            pdfStorage.deleteStoredPdf(storedPath);
            pdfStorage.deleteStoredPdf(finalMarkSchemePath);
            throw exception;
        }
    }

    public Optional<Path> getPdfPathForWorksheet(long worksheetId) throws SQLException {
        return worksheetRepository.findPdfPath(worksheetId)
                .flatMap(pdfStorage::resolvePdfPath)
                .filter(Files::isRegularFile);
    }

    public Optional<Path> getMarkSchemePdfPathForWorksheet(long worksheetId) throws SQLException {
        return worksheetRepository.findMarkSchemePdfPath(worksheetId)
                .flatMap(pdfStorage::resolvePdfPath)
                .filter(Files::isRegularFile);
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
        Optional<String> storedPdfPath = worksheetRepository.findPdfPath(worksheetId);
        Optional<String> storedMarkSchemePath =
                worksheetRepository.findMarkSchemePdfPath(worksheetId);
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
        storedPdfPath.ifPresent(pdfStorage::deleteStoredPdf);
        storedMarkSchemePath.ifPresent(pdfStorage::deleteStoredPdf);
    }

    private void validateWorksheet(
            String title, List<QuestionRepository.QuestionDraft> questionDrafts) {
        validateTitle(title);

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

    private void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Worksheet title cannot be empty.");
        }

        if (title.trim().length() > 120) {
            throw new IllegalArgumentException("Worksheet title must be 120 characters or fewer.");
        }
    }
}
