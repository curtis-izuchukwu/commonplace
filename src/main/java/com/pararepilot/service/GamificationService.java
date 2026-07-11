package com.pararepilot.service;

import java.sql.SQLException;

import com.pararepilot.model.UserStats;
import com.pararepilot.model.WorksheetAttempt;
import com.pararepilot.repository.AttemptRepository;
import com.pararepilot.repository.UserStatsRepository;
import com.pararepilot.util.DateUtils;

public class GamificationService {

    private static final int XP_WORKSHEET_COMPLETED = 50;
    private static final int XP_SCORE_70_PLUS = 20;
    private static final int XP_SCORE_90_PLUS = 40;
    private static final int XP_REFLECTION_COMPLETED = 10;
    private static final int XP_MISTAKE_REVISITED = 15;

    private final UserStatsRepository userStatsRepository;
    private final AttemptRepository attemptRepository;
    private final UserSettingsService userSettingsService;

    public GamificationService() {
        this(new UserStatsRepository(), new AttemptRepository(), new UserSettingsService());
    }

    public GamificationService(UserStatsRepository userStatsRepository) {
        this(userStatsRepository, new AttemptRepository(), new UserSettingsService());
    }

    public GamificationService(
            UserStatsRepository userStatsRepository,
            AttemptRepository attemptRepository
    ) {
        this(userStatsRepository, attemptRepository, new UserSettingsService());
    }

    public GamificationService(
            UserStatsRepository userStatsRepository,
            AttemptRepository attemptRepository,
            UserSettingsService userSettingsService
    ) {
        this.userStatsRepository = userStatsRepository;
        this.attemptRepository = attemptRepository;
        this.userSettingsService = userSettingsService;
    }

    public GamificationResult awardWorksheetCompletion(WorksheetAttempt attempt)
            throws SQLException {

        if (!attemptRepository.markXpAwardedIfPending(attempt.id(), DateUtils.now())) {
            UserStats existingStats = userStatsRepository.find();
            return new GamificationResult(
                    0,
                    calculateRank(existingStats.xp()),
                    existingStats
            );
        }

        int xpAwarded = calculateXpForAttempt(attempt);

        userStatsRepository.addXp(xpAwarded);

        UserStats updatedStats = userSettingsService.load().streakTrackingEnabled()
                ? userStatsRepository.updateStreakForCompletion(attempt.completedAt().toLocalDate())
                : userStatsRepository.find();

        return new GamificationResult(
                xpAwarded,
                calculateRank(updatedStats.xp()),
                updatedStats
        );
    }

    public GamificationResult awardMistakeReview() throws SQLException {
        UserStats updatedStats = userStatsRepository.addXp(XP_MISTAKE_REVISITED);

        return new GamificationResult(
                XP_MISTAKE_REVISITED,
                calculateRank(updatedStats.xp()),
                updatedStats
        );
    }

    public UserStats getUserStats() throws SQLException {
        return userStatsRepository.find();
    }

    public int calculateXpForAttempt(WorksheetAttempt attempt) {
        int xp = XP_WORKSHEET_COMPLETED + XP_REFLECTION_COMPLETED;

        if (attempt.scorePercent() >= 90) {
            xp += XP_SCORE_90_PLUS;
        } else if (attempt.scorePercent() >= 70) {
            xp += XP_SCORE_70_PLUS;
        }

        return xp;
    }

    public String calculateRank(int xp) {
        if (xp >= 5000) {
            return "Master";
        }

        if (xp >= 3000) {
            return "Specialist";
        }

        if (xp >= 1500) {
            return "Scholar";
        }

        if (xp >= 500) {
            return "Apprentice";
        }

        return "Novice";
    }

    public int xpForNextRank(int xp) {
        if (xp < 500) {
            return 500;
        }

        if (xp < 1500) {
            return 1500;
        }

        if (xp < 3000) {
            return 3000;
        }

        if (xp < 5000) {
            return 5000;
        }

        return xp;
    }
}
