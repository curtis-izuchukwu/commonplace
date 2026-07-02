package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.pararepilot.model.UserStats;
import com.pararepilot.service.AccountSession;
import com.pararepilot.util.DateUtils;

public class UserStatsRepository {

    public UserStats find() throws SQLException {
        String sql = """
                SELECT xp, streak_count, last_completion_date, worksheet_interval_days
                FROM user_stats
                WHERE user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return mapRow(rs);
                }
            }
        }

        ensureStatsRow();
        return find();
    }

    private void ensureStatsRow() throws SQLException {
        String sql = """
                INSERT OR IGNORE INTO user_stats
                    (user_id, xp, streak_count, worksheet_interval_days)
                VALUES
                    (?, 0, 0, 1);
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.executeUpdate();
        }
    }

    public UserStats addXp(int xpToAdd) throws SQLException {
        String sql = """
                UPDATE user_stats
                SET xp = xp + ?
                WHERE user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(0, xpToAdd));
            stmt.setLong(2, AccountSession.currentUserId());
            stmt.executeUpdate();
        }

        return find();
    }

    public UserStats updateStreakForCompletion(LocalDate completionDate) throws SQLException {
        UserStats currentStats = find();

        LocalDate resolvedCompletionDate = completionDate == null
                ? LocalDate.now()
                : completionDate;

        LocalDate lastCompletionDate = currentStats.lastCompletionDate();

        int newStreak;

        if (lastCompletionDate == null) {
            newStreak = 1;
        } else if (lastCompletionDate.isEqual(resolvedCompletionDate)) {
            newStreak = currentStats.streakCount();
        } else {
            long daysBetween = ChronoUnit.DAYS.between(lastCompletionDate, resolvedCompletionDate);

            if (daysBetween > 0 && daysBetween <= currentStats.worksheetIntervalDays()) {
                newStreak = currentStats.streakCount() + 1;
            } else {
                newStreak = 1;
            }
        }

        String sql = """
                UPDATE user_stats
                SET streak_count = ?,
                    last_completion_date = ?
                WHERE user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, newStreak);
            stmt.setString(2, DateUtils.toDatabaseDate(resolvedCompletionDate));
            stmt.setLong(3, AccountSession.currentUserId());
            stmt.executeUpdate();
        }

        return find();
    }

    public UserStats updateWorksheetIntervalDays(int intervalDays) throws SQLException {
        if (intervalDays <= 0) {
            throw new IllegalArgumentException("Worksheet interval must be at least 1 day.");
        }

        String sql = """
                UPDATE user_stats
                SET worksheet_interval_days = ?
                WHERE user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, intervalDays);
            stmt.setLong(2, AccountSession.currentUserId());
            stmt.executeUpdate();
        }

        return find();
    }

    public UserStats resetGamificationProgress() throws SQLException {
        String sql = """
                UPDATE user_stats
                SET xp = 0,
                    streak_count = 0,
                    last_completion_date = NULL
                WHERE user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.executeUpdate();
        }

        return find();
    }

    public UserStats resetTodaysRecommendationWindow() throws SQLException {
        new DailyRecommendationRepository().clear();

        String sql = """
                UPDATE user_stats
                SET last_completion_date = NULL
                WHERE user_id = ?;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.executeUpdate();
        }

        return find();
    }

    private UserStats mapRow(ResultSet rs) throws SQLException {
        return new UserStats(
                rs.getInt("xp"),
                rs.getInt("streak_count"),
                DateUtils.fromDatabaseDate(rs.getString("last_completion_date")),
                rs.getInt("worksheet_interval_days")
        );
    }
}
