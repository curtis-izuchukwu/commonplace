package com.commonplace.service;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Transparent, conservative estimates. These constants are product heuristics, not calibrated
 * probabilities.
 */
public final class LearningModel {
    public record Evidence(
            long attemptId,
            long worksheetId,
            String fingerprint,
            LocalDateTime at,
            double earned,
            double marks,
            String difficulty,
            boolean assisted,
            boolean mistake,
            String mode,
            int activeSeconds,
            String scope) {}

    public record Estimate(
            double mastery,
            double performance,
            double strength,
            double retention,
            double trend,
            double consistency,
            double weakness,
            int uniqueQuestions,
            int uniqueWorksheets,
            int uniqueScopes,
            int studyDays,
            int attempts,
            LocalDateTime lastAt,
            LocalDate dueAt) {
        public String certainty() {
            return strength < .35
                    ? "Limited evidence"
                    : strength < .7 ? "Developing evidence" : "Established evidence";
        }
    }

    private LearningModel() {}

    public static Estimate estimate(List<Evidence> history, LocalDate today) {
        List<Evidence> ordered =
                history.stream()
                        .sorted(
                                Comparator.comparing(Evidence::at)
                                        .thenComparingLong(Evidence::attemptId))
                        .toList();
        Map<String, LocalDateTime> last = new HashMap<>();
        Map<String, Double> evidenceByQuestion = new HashMap<>();
        Map<String, Double> deficits = new HashMap<>();
        Set<LocalDate> days = new HashSet<>();
        Set<Long> attempts = new HashSet<>();
        Set<Long> worksheets = new HashSet<>();
        Set<String> scopes = new HashSet<>();
        double earned = 0, weight = 0, square = 0, ceilingSum = 0;
        double stability = 2, previous = .5, recent = 0, recentWeight = 0, old = 0, oldWeight = 0;
        LocalDateTime lastAt = null;
        for (Evidence e : ordered) {
            double score = clamp(e.earned() / Math.max(1, e.marks()));
            LocalDateTime prior = last.put(e.fingerprint(), e.at());
            long gap =
                    prior == null
                            ? 0
                            : Math.max(
                                    0,
                                    ChronoUnit.DAYS.between(
                                            prior.toLocalDate(), e.at().toLocalDate()));
            double novelty = prior == null ? 1 : gap == 0 ? .02 : gap < 3 ? .25 : .65;
            double difficulty =
                    switch (e.difficulty()) {
                        case "EASY" -> .7;
                        case "HARD" -> 1.2;
                        default -> 1;
                    };
            double activeEvidence = Math.min(1, e.activeSeconds() / Math.max(10.0, e.marks() * 10));
            double reliability =
                    e.assisted()
                            ? .3
                            : "LOCKED_SELF".equals(e.mode()) ? .95 + .05 * activeEvidence : .65;
            double units = Math.min(8, e.marks()) * novelty * difficulty * reliability;
            double used = evidenceByQuestion.getOrDefault(e.fingerprint(), 0.0);
            units = Math.min(units, Math.max(0, 10 - used));
            evidenceByQuestion.put(e.fingerprint(), used + units);
            // Fresh success on a delayed return establishes retention; immediate repetition cannot
            // lengthen it.
            if (lastAt != null && e.at().toLocalDate().isAfter(lastAt.toLocalDate())) {
                if (score >= .7 && !e.assisted())
                    stability = Math.min(120, stability * (1.25 + .55 * score));
                else stability = Math.max(1, stability * .55);
            }
            double age = Math.max(0, ChronoUnit.DAYS.between(e.at().toLocalDate(), today));
            double decay = Math.exp(-Math.log(2) * age / 60.0);
            double w = units * decay;
            double effectiveScore =
                    e.assisted() ? score * .65 : e.mistake() ? Math.min(.6, score) : score;
            earned += w * effectiveScore;
            weight += w;
            square += w * effectiveScore * effectiveScore;
            ceilingSum +=
                    w
                            * ("EASY".equals(e.difficulty())
                                    ? .7
                                    : "HARD".equals(e.difficulty()) ? 1 : .9);
            deficits.put(
                    e.fingerprint(),
                    (1 - effectiveScore) * Math.min(1, e.marks() / 5.0) * Math.exp(-age / 45.0));
            if (age <= 14) {
                recent += w * effectiveScore;
                recentWeight += w;
            } else {
                old += w * effectiveScore;
                oldWeight += w;
            }
            days.add(e.at().toLocalDate());
            attempts.add(e.attemptId());
            worksheets.add(e.worksheetId());
            if (e.scope() != null && !e.scope().isBlank()) {
                scopes.add(fingerprint(e.scope()));
            }
            lastAt = e.at();
            previous = effectiveScore;
        }
        double performance = weight == 0 ? 0 : earned / weight;
        double consistency =
                weight == 0
                        ? 0
                        : clamp(
                                1
                                        - 2
                                                * Math.sqrt(
                                                        Math.max(
                                                                0,
                                                                square / weight
                                                                        - performance
                                                                                * performance)));
        double strength =
                (weight / (weight + 12))
                        * Math.min(1, evidenceByQuestion.size() / 10.0)
                        * Math.min(1, .55 + days.size() / 5.0)
                        * Math.min(1, .65 + worksheets.size() / 8.0)
                        * Math.min(1, .75 + scopes.size() / 12.0);
        // A successful return refreshes retention for the topic or worksheet; old evidence is not
        // forgotten twice.
        double retention =
                lastAt == null
                        ? 0
                        : Math.exp(
                                -Math.log(2)
                                        * Math.max(
                                                0,
                                                ChronoUnit.DAYS.between(
                                                        lastAt.toLocalDate(), today))
                                        / stability);
        double ceiling = weight == 0 ? 0 : ceilingSum / weight;
        double mastery =
                100 * performance * strength * (.8 + .2 * consistency) * retention * ceiling;
        double trend =
                oldWeight > 0 && recentWeight > 0 ? recent / recentWeight - old / oldWeight : 0;
        double weakness =
                deficits.values().stream().mapToDouble(Double::doubleValue).average().orElse(0);
        LocalDate due =
                lastAt == null
                        ? today
                        : lastAt.toLocalDate()
                                .plusDays(
                                        Math.max(
                                                1,
                                                Math.round(stability * (previous >= .7 ? 1 : .4))));
        return new Estimate(
                mastery,
                performance * 100,
                strength,
                retention,
                trend,
                consistency,
                weakness,
                evidenceByQuestion.size(),
                worksheets.size(),
                scopes.size(),
                days.size(),
                attempts.size(),
                lastAt,
                due);
    }

    public static double clamp(double n) {
        return Math.max(0, Math.min(1, n));
    }

    public static String fingerprint(String prompt) {
        return prompt.toLowerCase(Locale.ROOT).replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
    }
}
