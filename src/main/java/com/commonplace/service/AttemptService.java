package com.commonplace.service;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.WorksheetAttempt;
import com.commonplace.repository.AnswerRepository;
import com.commonplace.repository.AttemptRepository;
import com.commonplace.repository.MistakeRepository;
import com.commonplace.util.DateUtils;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class AttemptService {

    public record SubmissionResult(
            WorksheetAttempt attempt, GamificationResult practiceReward) {}

    private final AttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;
    private final MistakeRepository mistakeRepository;

    public AttemptService() {
        this(new AttemptRepository(), new AnswerRepository(), new MistakeRepository());
    }

    public AttemptService(
            AttemptRepository attemptRepository,
            AnswerRepository answerRepository,
            MistakeRepository mistakeRepository) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
        this.mistakeRepository = mistakeRepository;
    }

    public WorksheetAttempt submitAttempt(
            long worksheetId,
            LocalDateTime startedAt,
            List<AnswerRepository.AnswerDraft> answerDrafts)
            throws SQLException {
        return submitAttemptWithReward(worksheetId, startedAt, answerDrafts).attempt();
    }

    public SubmissionResult submitAttemptWithReward(
            long worksheetId,
            LocalDateTime startedAt,
            List<AnswerRepository.AnswerDraft> answerDrafts)
            throws SQLException {

        validateAttempt(answerDrafts);
        return com.commonplace.repository.DatabaseManager.transaction(
                () -> {
                    var worksheet =
                            new com.commonplace.repository.WorksheetRepository()
                                    .findById(worksheetId)
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Worksheet is not available in this"
                                                                    + " account."));
                    var questions =
                            new com.commonplace.repository.QuestionRepository()
                                    .findByWorksheetId(worksheetId);
                    var expected =
                            questions.stream()
                                    .collect(
                                            java.util.stream.Collectors.toMap(
                                                    com.commonplace.model.Question::id, q -> q));
                    var ids = new java.util.HashSet<Long>();
                    for (var answer : answerDrafts) {
                        var q = expected.get(answer.questionId());
                        if (q == null
                                || !ids.add(answer.questionId())
                                || q.maxMarks() != answer.maxMarks())
                            throw new IllegalArgumentException(
                                    "Attempt questions or marks do not match the worksheet.");
                    }
                    if (ids.size() != expected.size())
                        throw new IllegalArgumentException(
                                "Answer every question before submitting.");

                    int score =
                            answerDrafts.stream()
                                    .mapToInt(AnswerRepository.AnswerDraft::awardedMarks)
                                    .sum();

                    int maxScore =
                            answerDrafts.stream()
                                    .mapToInt(AnswerRepository.AnswerDraft::maxMarks)
                                    .sum();

                    double scorePercent = maxScore == 0 ? 0 : ((double) score / maxScore) * 100.0;

                    LocalDateTime completedAt = DateUtils.now();

                    WorksheetAttempt attempt =
                            attemptRepository.create(
                                    worksheetId,
                                    startedAt == null ? completedAt : startedAt,
                                    completedAt,
                                    score,
                                    maxScore,
                                    scorePercent,
                                    ConfidenceLevel.MEDIUM,
                                    null,
                                    null,
                                    null);

                    answerRepository.createMany(attempt.id(), answerDrafts);
                    mistakeRepository.createFromAttempt(attempt.id());
                    new ReflectionService().updateWorksheetStats(worksheet, attempt);
                    new LearningService().refresh(worksheet.topicId());
                    GamificationResult reward =
                            new GamificationService().awardWorksheetCompletion(attempt);
                    return new SubmissionResult(attempt, reward);
                });
    }

    private void validateAttempt(List<AnswerRepository.AnswerDraft> answerDrafts) {
        if (answerDrafts == null || answerDrafts.isEmpty()) {
            throw new IllegalArgumentException("Cannot submit an attempt with no answers.");
        }

        for (int i = 0; i < answerDrafts.size(); i++) {
            AnswerRepository.AnswerDraft draft = answerDrafts.get(i);

            if (draft.userAnswer() == null || draft.userAnswer().isBlank()) {
                throw new IllegalArgumentException("Question " + (i + 1) + " needs an answer.");
            }

            if (draft.maxMarks() <= 0) {
                throw new IllegalArgumentException(
                        "Question " + (i + 1) + " has invalid max marks.");
            }

            if (draft.awardedMarks() < 0) {
                throw new IllegalArgumentException(
                        "Question " + (i + 1) + " cannot have negative marks.");
            }

            if (draft.awardedMarks() > draft.maxMarks()) {
                throw new IllegalArgumentException(
                        "Question "
                                + (i + 1)
                                + " cannot be awarded more than "
                                + draft.maxMarks()
                                + " marks.");
            }
        }
    }
}
