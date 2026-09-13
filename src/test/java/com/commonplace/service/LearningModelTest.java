package com.commonplace.service;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import java.time.*;
import java.util.*;

class LearningModelTest {
    private final LocalDate today = LocalDate.of(2026, 9, 12);

    private LearningModel.Evidence evidence(
            int question, int day, boolean assisted, String difficulty, int marks) {
        return new LearningModel.Evidence(
                day * 100L + question,
                day + 1,
                "question " + question,
                today.minusDays(day).atTime(12, 0),
                marks,
                marks,
                difficulty,
                assisted,
                false,
                "LOCKED_SELF",
                120,
                "Scope " + Math.floorDiv(question, 3));
    }

    @Test
    void threePerfectAnswersProvideLimitedEvidence() {
        var result =
                LearningModel.estimate(
                        List.of(
                                evidence(1, 0, false, "MEDIUM", 3),
                                evidence(2, 0, false, "MEDIUM", 3),
                                evidence(3, 0, false, "MEDIUM", 3)),
                        today);
        assertEquals(100, result.performance());
        assertTrue(result.mastery() < 25);
        assertEquals("Limited evidence", result.certainty());
    }

    @Test
    void repeatedIdenticalQuestionsCannotCreateBreadth() {
        var repeated = new ArrayList<LearningModel.Evidence>();
        for (int i = 0; i < 100; i++) repeated.add(evidence(1, 0, false, "HARD", 3));
        var result = LearningModel.estimate(repeated, today);
        assertEquals(1, result.uniqueQuestions());
        assertTrue(result.mastery() < 10);
    }

    @Test
    void spacedIndependentPracticeIncreasesEvidenceAndRetention() {
        var single = new ArrayList<LearningModel.Evidence>();
        var spaced = new ArrayList<LearningModel.Evidence>();
        for (int q = 0; q < 8; q++) {
            single.add(evidence(q, 0, false, "HARD", 5));
            for (int d : List.of(0, 3, 6, 9, 12)) spaced.add(evidence(q, d, false, "HARD", 5));
        }
        var a = LearningModel.estimate(single, today);
        var b = LearningModel.estimate(spaced, today);
        assertTrue(b.mastery() > a.mastery());
        assertTrue(b.strength() > a.strength());
        assertTrue(b.dueAt().isAfter(a.dueAt()));
        assertTrue(LearningModel.estimate(spaced, today.plusDays(90)).mastery() < b.mastery());
    }

    @Test
    void assistanceDifficultyAndMarksAffectEvidenceWithoutSpeedRequirements() {
        var independent = LearningModel.estimate(List.of(evidence(1, 0, false, "HARD", 8)), today);
        var assisted = LearningModel.estimate(List.of(evidence(1, 0, true, "HARD", 8)), today);
        var easy = LearningModel.estimate(List.of(evidence(1, 0, false, "EASY", 8)), today);
        var shortQuestion =
                LearningModel.estimate(List.of(evidence(1, 0, false, "HARD", 1)), today);
        assertTrue(independent.mastery() > assisted.mastery());
        assertTrue(independent.mastery() > easy.mastery());
        assertTrue(independent.strength() > shortQuestion.strength());
    }

    @Test
    void variedWorksheetsAndScopesIncreaseBreadthAutomatically() {
        List<LearningModel.Evidence> narrow = new ArrayList<>();
        List<LearningModel.Evidence> varied = new ArrayList<>();
        for (int question = 0; question < 9; question++) {
            var evidence = evidence(question, 0, false, "HARD", 5);
            narrow.add(
                    new LearningModel.Evidence(
                            evidence.attemptId(),
                            1,
                            evidence.fingerprint(),
                            evidence.at(),
                            evidence.earned(),
                            evidence.marks(),
                            evidence.difficulty(),
                            false,
                            false,
                            evidence.mode(),
                            120,
                            "AVL rotations"));
            varied.add(
                    new LearningModel.Evidence(
                            evidence.attemptId(),
                            1 + question / 3,
                            evidence.fingerprint(),
                            evidence.at(),
                            evidence.earned(),
                            evidence.marks(),
                            evidence.difficulty(),
                            false,
                            false,
                            evidence.mode(),
                            120,
                            "BST focus " + question / 3));
        }
        var narrowEstimate = LearningModel.estimate(narrow, today);
        var variedEstimate = LearningModel.estimate(varied, today);
        assertEquals(1, narrowEstimate.uniqueWorksheets());
        assertEquals(1, narrowEstimate.uniqueScopes());
        assertEquals(3, variedEstimate.uniqueWorksheets());
        assertEquals(3, variedEstimate.uniqueScopes());
        assertTrue(variedEstimate.mastery() > narrowEstimate.mastery());
    }

    @Test
    void duplicatePromptFormattingHasOneIdentity() {
        assertEquals(
                LearningModel.fingerprint("Explain: AVL rotations!"),
                LearningModel.fingerprint("explain avl   rotations"));
    }
}
