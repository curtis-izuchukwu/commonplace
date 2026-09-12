package com.commonplace.service;

import java.util.List;
import java.util.Optional;

import com.commonplace.model.StudyModule;
import com.commonplace.model.Topic;
import com.commonplace.model.UserStats;
import com.commonplace.model.UserSettings;
import com.commonplace.repository.AttemptRepository;

public record DashboardSummary(
        UserStats userStats,
        UserSettings userSettings,
        String rank,
        int nextRankXp,
        Optional<WorksheetRecommendation> recommendation,
        List<StudyModule> modules,
        List<Topic> weakestTopics,
        List<AttemptRepository.RecentAttemptDisplayItem> recentAttempts,
        int unresolvedMistakeCount,
        int completedWorksheetsToday,
        boolean worksheetWindowLocked,
        List<DashboardReminder> reminders
) {
}
