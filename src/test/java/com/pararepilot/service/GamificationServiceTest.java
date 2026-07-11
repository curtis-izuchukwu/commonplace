package com.pararepilot.service;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import org.junit.jupiter.api.Test;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.WorksheetAttempt;

class GamificationServiceTest {

    private final GamificationService service = new GamificationService();

    @Test
    void calculatesBaseCompletionAndReflectionXp() {
        WorksheetAttempt attempt = new WorksheetAttempt(
                1,
                1,
                LocalDateTime.now().minusMinutes(20),
                LocalDateTime.now(),
                5,
                10,
                50.0,
                ConfidenceLevel.MEDIUM,
                null,
                null,
                null
        );

        assertEquals(60, service.calculateXpForAttempt(attempt));
    }

    @Test
    void calculatesSeventyPlusXpBonus() {
        WorksheetAttempt attempt = new WorksheetAttempt(
                1,
                1,
                LocalDateTime.now().minusMinutes(20),
                LocalDateTime.now(),
                7,
                10,
                70.0,
                ConfidenceLevel.MEDIUM,
                null,
                null,
                null
        );

        assertEquals(80, service.calculateXpForAttempt(attempt));
    }

    @Test
    void calculatesNinetyPlusXpBonus() {
        WorksheetAttempt attempt = new WorksheetAttempt(
                1,
                1,
                LocalDateTime.now().minusMinutes(20),
                LocalDateTime.now(),
                9,
                10,
                90.0,
                ConfidenceLevel.HIGH,
                null,
                null,
                null
        );

        assertEquals(100, service.calculateXpForAttempt(attempt));
    }

    @Test
    void calculatesRanks() {
        assertEquals("Novice", service.calculateRank(0));
        assertEquals("Apprentice", service.calculateRank(500));
        assertEquals("Scholar", service.calculateRank(1500));
        assertEquals("Specialist", service.calculateRank(3000));
        assertEquals("Master", service.calculateRank(5000));
    }
}