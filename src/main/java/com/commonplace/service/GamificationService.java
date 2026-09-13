package com.commonplace.service;

import com.commonplace.model.*;
import com.commonplace.repository.*;
import com.commonplace.util.DateUtils;

import java.sql.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;

/** XP measures study activity; mastery is a separate estimate of retained skill. */
public class GamificationService {
    private final UserStatsRepository stats;
    private final AttemptRepository attempts;
    private final UserSettingsService settings;

    public GamificationService() {
        this(new UserStatsRepository(), new AttemptRepository(), new UserSettingsService());
    }

    public GamificationService(UserStatsRepository s) {
        this(s, new AttemptRepository(), new UserSettingsService());
    }

    public GamificationService(UserStatsRepository s, AttemptRepository a) {
        this(s, a, new UserSettingsService());
    }

    public GamificationService(UserStatsRepository s, AttemptRepository a, UserSettingsService u) {
        stats = s;
        attempts = a;
        settings = u;
    }

    public GamificationResult awardWorksheetCompletion(WorksheetAttempt supplied)
            throws SQLException {
        return DatabaseManager.transaction(
                () -> {
                    WorksheetAttempt a =
                            attempts.findById(supplied.id())
                                    .orElseThrow(
                                            () ->
                                                    new IllegalArgumentException(
                                                            "Attempt not available."));
                    if (!attempts.markXpAwardedIfPending(a.id(), DateUtils.now())) return result(0);
                    stats.find();
                    int amount =
                            recordEvent(
                                    "attempt:" + a.id(),
                                    "PRACTICE",
                                    calculateXpForAttempt(a),
                                    a.completedAt().toLocalDate(),
                                    250);
                    // Daily goal counts distinct worksheets, so repeating one worksheet cannot fill
                    // a larger
                    // goal.
                    if (settings.load().streakTrackingEnabled()
                            && attempts.countCompletedOn(a.completedAt().toLocalDate())
                                    >= settings.load().dailyWorksheetGoal())
                        stats.updateStreakForCompletion(a.completedAt().toLocalDate());
                    return result(amount);
                });
    }

    public GamificationResult awardReflection(
            long attemptId, String weakness, String action, String notes) throws SQLException {
        if (attempts.findById(attemptId).isEmpty())
            throw new IllegalArgumentException("Attempt not available.");
        String content =
                (Objects.toString(weakness, "")
                                + " "
                                + Objects.toString(action, "")
                                + " "
                                + Objects.toString(notes, ""))
                        .trim();
        if (content.length() < 30
                || Objects.toString(action, "").trim().length() < 10
                || "Reflection skipped.".equals(content)) return result(0);
        return DatabaseManager.transaction(
                () ->
                        result(
                                recordEvent(
                                        "reflection:" + attemptId,
                                        "REFLECTION",
                                        5,
                                        LocalDate.now(),
                                        30)));
    }

    /** Merely opening or marking a mistake as revisited carries no reward. */
    public GamificationResult awardMistakeReview() throws SQLException {
        return result(0);
    }

    public GamificationResult awardMistakeRecall(
            long topicId, String fingerprint, boolean success, boolean assisted)
            throws SQLException {
        if (!success || assisted) return result(0);
        String key = java.util.HexFormat.of().formatHex(sha256(fingerprint));
        return DatabaseManager.transaction(
                () ->
                        result(
                                recordEvent(
                                        "review:" + topicId + ":" + key + ":" + LocalDate.now(),
                                        "RECALL",
                                        10,
                                        LocalDate.now(),
                                        30)));
    }

    private byte[] sha256(String value) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private int recordEvent(String key, String kind, int requested, LocalDate date, int categoryCap)
            throws SQLException {
        stats.find();
        long user = AccountSession.currentUserId();
        try (Connection c = DatabaseManager.connect()) {
            int dayTotal = 0, categoryTotal = 0;
            try (PreparedStatement s =
                    c.prepareStatement(
                            "SELECT kind,COALESCE(SUM(amount),0) amount FROM xp_events WHERE"
                                    + " user_id=? AND substr(earned_at,1,10)=? GROUP BY kind")) {
                s.setLong(1, user);
                s.setString(2, date.toString());
                try (ResultSet r = s.executeQuery()) {
                    while (r.next()) {
                        dayTotal += r.getInt("amount");
                        if (kind.equals(r.getString("kind"))) categoryTotal = r.getInt("amount");
                    }
                }
            }
            int amount =
                    Math.max(
                            0,
                            Math.min(
                                    requested,
                                    Math.min(300 - dayTotal, categoryCap - categoryTotal)));
            try (PreparedStatement s =
                    c.prepareStatement(
                            "INSERT OR IGNORE INTO"
                                    + " xp_events(user_id,event_key,kind,amount,earned_at)"
                                    + " VALUES(?,?,?,?,?)")) {
                s.setLong(1, user);
                s.setString(2, key);
                s.setString(3, kind);
                s.setInt(4, amount);
                s.setString(5, date + "T" + LocalTime.now());
                if (s.executeUpdate() == 0) return 0;
            }
            stats.addXp(amount);
            return amount;
        }
    }

    public int calculateXpForAttempt(WorksheetAttempt a) throws SQLException {
        record Prior(LocalDate date, double score) {}
        Map<String, Prior> prior = new HashMap<>();
        List<String> current = new ArrayList<>();
        double xp = 0;
        try (Connection c = DatabaseManager.connect();
                PreparedStatement s =
                        c.prepareStatement(
                                """
SELECT q.prompt,a.awarded_marks,a.max_marks,a.assisted,a.active_seconds,a.evidence_mode,
    wa.id,wa.completed_at,COALESCE(q.assessed_difficulty,w.difficulty) difficulty
FROM answers a JOIN questions q ON q.id=a.question_id JOIN worksheet_attempts wa ON wa.id=a.attempt_id
JOIN worksheets w ON w.id=wa.worksheet_id JOIN topics t ON t.id=w.topic_id JOIN modules m ON m.id=t.module_id
WHERE m.user_id=? AND wa.id<=? ORDER BY wa.completed_at,wa.id,a.id
""")) {
            s.setLong(1, AccountSession.currentUserId());
            s.setLong(2, a.id());
            try (ResultSet r = s.executeQuery()) {
                while (r.next()) {
                    String fp = LearningModel.fingerprint(r.getString("prompt"));
                    double score =
                            (double) r.getInt("awarded_marks") / Math.max(1, r.getInt("max_marks"));
                    LocalDate date =
                            DateUtils.fromDatabaseDateTime(r.getString("completed_at"))
                                    .toLocalDate();
                    if (r.getLong("id") != a.id()) {
                        prior.put(fp, new Prior(date, score));
                        continue;
                    }
                    if (current.contains(fp)) continue;
                    current.add(fp);
                    Prior p = prior.get(fp);
                    long gap = p == null ? 0 : ChronoUnit.DAYS.between(p.date(), date);
                    double novelty =
                            p == null ? 1 : gap <= 0 ? 0 : gap < 3 ? .15 : gap < 7 ? .5 : .8;
                    double difficulty =
                            switch (r.getString("difficulty")) {
                                case "EASY" -> .8;
                                case "HARD" -> 1.15;
                                default -> 1;
                            };
                    double marks = Math.min(8, r.getInt("max_marks"));
                    double reliability =
                            r.getBoolean("assisted")
                                    ? .45
                                    : "LOCKED_SELF".equals(r.getString("evidence_mode")) ? 1 : .8;
                    double improvement = p == null ? 0 : Math.max(0, score - p.score());
                    double recall = gap >= 3 && score >= .7 && !r.getBoolean("assisted") ? .4 : 0;
                    double active =
                            Math.min(1, r.getInt("active_seconds") / Math.max(30.0, marks * 30));
                    xp +=
                            novelty
                                    * reliability
                                    * difficulty
                                    * (marks * (1.5 + 1.5 * score + improvement + recall) + active);
                }
            }
        }
        return (int) Math.round(Math.min(120, xp));
    }

    private GamificationResult result(int amount) throws SQLException {
        UserStats s = stats.find();
        return new GamificationResult(amount, calculateRank(s.xp()), s);
    }

    public UserStats getUserStats() throws SQLException {
        return stats.find();
    }

    public String calculateRank(int xp) {
        return xp >= 5000
                ? "Dedicated learner"
                : xp >= 3000
                        ? "Steady learner"
                        : xp >= 1500
                                ? "Regular learner"
                                : xp >= 500 ? "Building a habit" : "Getting started";
    }

    public int xpForNextRank(int xp) {
        return xp < 500 ? 500 : xp < 1500 ? 1500 : xp < 3000 ? 3000 : xp < 5000 ? 5000 : xp;
    }

    public int xpForCurrentRank(int xp) {
        return xp >= 5000 ? 5000 : xp >= 3000 ? 3000 : xp >= 1500 ? 1500 : xp >= 500 ? 500 : 0;
    }

    public double rankProgress(int xp) {
        int start = xpForCurrentRank(xp), end = xpForNextRank(xp);
        return end <= start ? 1 : (double) (xp - start) / (end - start);
    }

    public String streakLabel(UserStats s) {
        return s.streakCount() + " study session" + (s.streakCount() == 1 ? "" : "s") + " in a row";
    }
}
