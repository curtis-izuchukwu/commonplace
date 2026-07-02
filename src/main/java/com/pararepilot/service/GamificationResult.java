package com.pararepilot.service;

import com.pararepilot.model.UserStats;

public record GamificationResult(
        int xpAwarded,
        String rank,
        UserStats updatedStats
) {
}