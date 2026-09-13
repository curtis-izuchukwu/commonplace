package com.commonplace.service;

import com.commonplace.model.*;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

public class PriorityScoreService {
    public record Signals(
            double mastery,
            double performance,
            double strength,
            double retention,
            double weakness,
            double trend,
            double consistency,
            LocalDate due,
            double difficultyFit,
            double diversity,
            double examUrgency) {}

    public record Priority(int score, String explanation) {}

    public Priority evaluate(Worksheet worksheet, Topic topic, Signals s, String focus) {
        double performanceRisk =
                Math.max(
                        1 - s.performance() / 100, Math.max(s.weakness(), Math.max(0, -s.trend())));
        double need =
                LearningModel.clamp(
                        .55 * (1 - s.mastery() / 100)
                                + .30 * performanceRisk
                                + .15 * (1 - s.consistency()));
        long overdue = ChronoUnit.DAYS.between(s.due(), LocalDate.now());
        double scheduleNeed = overdue >= 0 ? Math.min(1, .7 + overdue / 30.0) : .2 / (1 - overdue);
        double reviewNeed = Math.max(scheduleNeed, 1 - LearningModel.clamp(s.retention()));
        double uncertainty = 1 - s.strength();
        double importance =
                topic.importance() == ImportanceLevel.HIGH
                        ? 1
                        : topic.importance() == ImportanceLevel.MEDIUM ? .6 : .3;
        importance =
                (importance
                                + (worksheet.importance() == ImportanceLevel.HIGH
                                        ? 1
                                        : worksheet.importance() == ImportanceLevel.MEDIUM ? .6 : .3))
                        / 2;
        double focusNeed = "WEAK_TOPICS".equals(focus) ? .42 : .32;
        double focusExam = "UPCOMING_EXAMS".equals(focus) ? .22 : .12;
        double raw =
                focusNeed * need
                        + .18 * reviewNeed
                        + .10 * uncertainty
                        + .08 * importance
                        + .08 * s.diversity()
                        + .10 * s.difficultyFit()
                        + focusExam * s.examUrgency();
        double normalizer = focusNeed + .18 + .10 + .08 + .08 + .10 + focusExam;
        int score = (int) Math.round(100 * raw / normalizer);
        score = Math.max(1, Math.min(100, score));
        List<String> reasons = new ArrayList<>();
        if (overdue >= 0) reasons.add("Review is due");
        else if (s.retention() < .65) reasons.add("Recall may be fading");
        if (s.strength() < .35) reasons.add("More evidence needed");
        else if (performanceRisk > .4) reasons.add("Worth reinforcing");
        if (s.examUrgency() > .08) reasons.add("Exam approaching");
        if (s.diversity() > .8) reasons.add("Adds variety");
        if (s.difficultyFit() < .6) reasons.add("Stretch difficulty");
        if (reasons.isEmpty()) reasons.add("A good fit for your current study plan");
        return new Priority(score, String.join("  •  ", reasons.stream().limit(3).toList()));
    }

    public int calculatePriority(Worksheet w, Topic t) {
        return calculatePriority(w, t, 0);
    }

    public int calculatePriority(Worksheet w, Topic t, int mistakes) {
        return evaluate(w, t, fallback(w, t, mistakes), "BALANCED").score();
    }

    public String explainPriority(Worksheet w, Topic t, int mistakes) {
        return evaluate(w, t, fallback(w, t, mistakes), "BALANCED").explanation();
    }

    private Signals fallback(Worksheet w, Topic t, int mistakes) {
        if (w == null || t == null)
            throw new IllegalArgumentException("Worksheet and topic are required.");
        double latest = w.latestScorePercent() == null ? 0 : w.latestScorePercent();
        double average = w.averageScorePercent() == null ? latest : w.averageScorePercent();
        LocalDate due =
                w.lastAttemptedAt() == null
                        ? LocalDate.now()
                        : w.lastAttemptedAt()
                                .toLocalDate()
                                .plusDays(latest >= 85 ? 7 : latest >= 60 ? 3 : 1);
        double fit =
                w.difficulty() == DifficultyLevel.HARD
                        ? (average >= 70 ? 1 : .3)
                        : w.difficulty() == DifficultyLevel.EASY ? (average < 70 ? 1 : .65) : .85;
        return new Signals(
                t.masteryScore(),
                (latest + average) / 2,
                w.timesAttempted() / (w.timesAttempted() + 5.0),
                1,
                Math.min(1, mistakes / 5.0),
                (latest - average) / 100,
                .7,
                due,
                fit,
                1,
                0);
    }
}
