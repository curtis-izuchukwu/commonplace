package com.commonplace.model;

import java.time.LocalDateTime;

public record MistakeBankItem(
        long id,
        long topicId,
        long worksheetId,
        long questionId,
        long attemptId,
        String userAnswer,
        String markScheme,
        String mistakeNote,
        LocalDateTime createdAt,
        boolean resolved,
        int timesRevisited
) {
}