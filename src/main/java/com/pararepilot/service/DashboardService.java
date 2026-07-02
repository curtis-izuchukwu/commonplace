package com.pararepilot.service;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import com.pararepilot.model.Topic;
import com.pararepilot.model.UserStats;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.repository.MistakeRepository;
import com.pararepilot.repository.TopicRepository;

public class DashboardService {

    private static final int WEAKEST_TOPIC_LIMIT = 5;
    private static final int RECENT_ATTEMPT_LIMIT = 5;

    private final GamificationService gamificationService;
    private final WorksheetSelectionService worksheetSelectionService;
    private final TopicRepository topicRepository;
    private final AttemptRepository attemptRepository;
    private final MistakeRepository mistakeRepository;

    public DashboardService() {
        this(
                new GamificationService(),
                new WorksheetSelectionService(),
                new TopicRepository(),
                new AttemptRepository(),
                new MistakeRepository()
        );
    }

    public DashboardService(
            GamificationService gamificationService,
            WorksheetSelectionService worksheetSelectionService,
            TopicRepository topicRepository,
            AttemptRepository attemptRepository,
            MistakeRepository mistakeRepository
    ) {
        this.gamificationService = gamificationService;
        this.worksheetSelectionService = worksheetSelectionService;
        this.topicRepository = topicRepository;
        this.attemptRepository = attemptRepository;
        this.mistakeRepository = mistakeRepository;
    }

    public DashboardSummary loadDashboard() throws SQLException {
        UserStats userStats = gamificationService.getUserStats();
        String rank = gamificationService.calculateRank(userStats.xp());
        int nextRankXp = gamificationService.xpForNextRank(userStats.xp());

        Optional<WorksheetRecommendation> recommendation =
                worksheetSelectionService.recommendWorksheet();

        List<Topic> weakestTopics =
                topicRepository.findWeakestTopics(WEAKEST_TOPIC_LIMIT);

        List<AttemptRepository.RecentAttemptDisplayItem> recentAttempts =
                attemptRepository.findRecentDisplayItems(RECENT_ATTEMPT_LIMIT);

        int unresolvedMistakeCount =
                mistakeRepository.countUnresolved();

        return new DashboardSummary(
                userStats,
                rank,
                nextRankXp,
                recommendation,
                weakestTopics,
                recentAttempts,
                unresolvedMistakeCount
        );
    }
}