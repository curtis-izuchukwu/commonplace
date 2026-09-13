package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class GamificationServiceTest {
    private final GamificationService service = new GamificationService();

    @Test
    void rankProgressMeasuresCurrentBand() {
        assertEquals(0, service.rankProgress(500));
        assertEquals(.5, service.rankProgress(1000));
        assertEquals(0, service.rankProgress(1500));
        assertEquals(1, service.rankProgress(5000));
    }

    @Test
    void ranksDescribeStudyHabitsRatherThanSubjectCompetence() {
        assertFalse(service.calculateRank(5000).contains("Master"));
        assertNotEquals(service.calculateRank(0), service.calculateRank(5000));
    }
}
