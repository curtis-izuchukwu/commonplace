package com.pararepilot.service;

import java.util.List;
import java.util.Optional;

import com.pararepilot.model.Topic;
import com.pararepilot.model.UserStats;
import com.pararepilot.repository.AttemptRepository;

public record DashboardSummary(
        UserStats userStats,
        String rank,
        int nextRankXp,
        Optional<WorksheetRecommendation> recommendation,
        List<Topic> weakestTopics,
        List<AttemptRepository.RecentAttemptDisplayItem> recentAttempts,
        int unresolvedMistakeCount
) {
}