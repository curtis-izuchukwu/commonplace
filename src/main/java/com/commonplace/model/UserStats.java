package com.commonplace.model;

import java.time.LocalDate;

public record UserStats(
        int xp,
        int streakCount,
        LocalDate lastCompletionDate,
        int worksheetIntervalDays
) {
}