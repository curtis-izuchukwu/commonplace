package com.commonplace.repository;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import org.junit.jupiter.api.Test;

import com.commonplace.model.UserStats;

class UserStatsRepositoryTest {

    private final UserStatsRepository repository = new UserStatsRepository();

    @Test
    void streakRemainsAliveOnDueDay() {
        LocalDate today = LocalDate.of(2026, 7, 10);
        UserStats stats = new UserStats(
                860,
                2,
                today.minusDays(1),
                1
        );

        UserStats normalized = repository.expireStaleStreak(stats, today);

        assertEquals(2, normalized.streakCount());
        assertEquals(today.minusDays(1), normalized.lastCompletionDate());
    }

    @Test
    void streakExpiresAfterMissedDueDay() {
        LocalDate today = LocalDate.of(2026, 7, 10);
        UserStats stats = new UserStats(
                860,
                2,
                today.minusDays(3),
                1
        );

        UserStats normalized = repository.expireStaleStreak(stats, today);

        assertEquals(0, normalized.streakCount());
        assertNull(normalized.lastCompletionDate());
        assertEquals(860, normalized.xp());
    }

    @Test
    void customWorksheetIntervalControlsExpiryWindow() {
        LocalDate today = LocalDate.of(2026, 7, 10);
        UserStats aliveStats = new UserStats(
                860,
                2,
                today.minusDays(2),
                2
        );
        UserStats staleStats = new UserStats(
                860,
                2,
                today.minusDays(3),
                2
        );

        assertEquals(2, repository.expireStaleStreak(aliveStats, today).streakCount());
        assertEquals(0, repository.expireStaleStreak(staleStats, today).streakCount());
    }
}
