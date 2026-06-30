package com.pararepilot.model;

public record Answer(
        long id,
        long attemptId,
        long questionId,
        String userAnswer,
        int awardedMarks,
        int maxMarks,
        boolean markedAsMistake,
        String mistakeNote
) {
}