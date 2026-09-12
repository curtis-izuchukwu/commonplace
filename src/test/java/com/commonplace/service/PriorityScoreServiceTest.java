package com.commonplace.service;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import com.commonplace.model.ConfidenceLevel;
import com.commonplace.model.DifficultyLevel;
import com.commonplace.model.ImportanceLevel;
import com.commonplace.model.Topic;
import com.commonplace.model.Worksheet;

class PriorityScoreServiceTest {

    private final PriorityScoreService service = new PriorityScoreService();

    @Test
    void weakImportantNeverAttemptedWorksheetGetsHighPriority() {
        Worksheet worksheet = new Worksheet(
                1,
                1,
                "Hard Graphs Worksheet",
                "Graph traversal practice",
                DifficultyLevel.HARD,
                ImportanceLevel.HIGH,
                "manual",
                LocalDateTime.now().minusDays(10),
                null,
                0,
                null,
                null,
                0
        );

        Topic topic = new Topic(
                1,
                1,
                "Graphs",
                "DFS and BFS",
                ImportanceLevel.HIGH,
                ConfidenceLevel.LOW,
                20,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(1)
        );

        int priority = service.calculatePriority(worksheet, topic);

        assertEquals(100, priority);
    }

    @Test
    void strongRecentLowImportanceWorksheetGetsLowerPriority() {
        Worksheet worksheet = new Worksheet(
                1,
                1,
                "Easy Recap",
                "Already understood material",
                DifficultyLevel.EASY,
                ImportanceLevel.LOW,
                "manual",
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now(),
                3,
                95.0,
                92.0,
                0
        );

        Topic topic = new Topic(
                1,
                1,
                "Intro Topic",
                "Easy material",
                ImportanceLevel.LOW,
                ConfidenceLevel.HIGH,
                90,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now()
        );

        int priority = service.calculatePriority(worksheet, topic);

        assertTrue(priority < 30);
        assertEquals(15, priority);
    }

    @Test
    void failureStreakAndMistakesIncreasePriority() {
        Worksheet worksheet = new Worksheet(
                1,
                1,
                "BST Deletion",
                "Deletion cases",
                DifficultyLevel.MEDIUM,
                ImportanceLevel.MEDIUM,
                "manual",
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now().minusDays(3),
                2,
                65.0,
                62.0,
                1
        );

        Topic topic = new Topic(
                1,
                1,
                "Binary Search Trees",
                "BST operations",
                ImportanceLevel.LOW,
                ConfidenceLevel.MEDIUM,
                40,
                LocalDateTime.now().minusDays(10),
                LocalDateTime.now()
        );

        int withoutMistakes = service.calculatePriority(worksheet, topic, 0);
        int withMistakes = service.calculatePriority(worksheet, topic, 3);

        assertTrue(withMistakes > withoutMistakes);
        assertEquals(76, withoutMistakes);
        assertEquals(91, withMistakes);
    }
}
