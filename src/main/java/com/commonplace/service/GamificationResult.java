package com.commonplace.service;

import com.commonplace.model.UserStats;

public record GamificationResult(
        int xpAwarded,
        String rank,
        UserStats updatedStats
) {
}