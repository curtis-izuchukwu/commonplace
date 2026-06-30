package com.pararepilot.service;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.repository.AnswerRepository;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.util.DateUtils;

public class AttemptService {

    private final AttemptRepository attemptRepository;
    private final AnswerRepository answerRepository;

    public AttemptService() {
        this(new AttemptRepository(), new AnswerRepository());
    }

    public AttemptService(
            AttemptRepository attemptRepository,
            AnswerRepository answerRepository
    ) {
        this.attemptRepository = attemptRepository;
        this.answerRepository = answerRepository;
    }

    public WorksheetAttempt submitAttempt(
            long worksheetId,
            LocalDateTime startedAt,
            List<AnswerRepository.AnswerDraft> answerDrafts
    ) throws SQLException {

        validateAttempt(answerDrafts);

        int score = answerDrafts.stream()
                .mapToInt(AnswerRepository.AnswerDraft::awardedMarks)
                .sum();

        int maxScore = answerDrafts.stream()
                .mapToInt(AnswerRepository.AnswerDraft::maxMarks)
                .sum();

        double scorePercent = maxScore == 0
                ? 0
                : ((double) score / maxScore) * 100.0;

        LocalDateTime completedAt = DateUtils.now();

        /*
         * Reflection belongs to the next branch.
         * The database requires confidence_after to be non-null,
         * so MEDIUM is used as a temporary default.
         */
        WorksheetAttempt attempt = attemptRepository.create(
                worksheetId,
                startedAt == null ? completedAt : startedAt,
                completedAt,
                score,
                maxScore,
                scorePercent,
                ConfidenceLevel.MEDIUM,
                null,
                null,
                null
        );

        try {
            answerRepository.createMany(attempt.id(), answerDrafts);
            return attempt;
        } catch (SQLException e) {
            // Later we can add transaction handling.
            // For V1 branch scope, report the failure clearly.
            throw new SQLException("Attempt was created but answers failed to save.", e);
        }
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
                throw new IllegalArgumentException("Question " + (i + 1) + " has invalid max marks.");
            }

            if (draft.awardedMarks() < 0) {
                throw new IllegalArgumentException("Question " + (i + 1) + " cannot have negative marks.");
            }

            if (draft.awardedMarks() > draft.maxMarks()) {
                throw new IllegalArgumentException(
                        "Question " + (i + 1) + " cannot be awarded more than "
                                + draft.maxMarks() + " marks."
                );
            }
        }
    }
}