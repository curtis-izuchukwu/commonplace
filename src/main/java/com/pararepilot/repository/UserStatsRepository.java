package com.pararepilot.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.pararepilot.model.UserStats;
import com.pararepilot.util.DateUtils;

public class UserStatsRepository {

    public UserStats find() throws SQLException {
        String sql = """
                SELECT xp, streak_count, last_completion_date, worksheet_interval_days
                FROM user_stats
                WHERE id = 1;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                return mapRow(rs);
            }

            throw new SQLException("User stats row was not found.");
        }
    }

    public UserStats addXp(int xpToAdd) throws SQLException {
        String sql = """
                UPDATE user_stats
                SET xp = xp + ?
                WHERE id = 1;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, Math.max(0, xpToAdd));
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
                WHERE id = 1;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, newStreak);
            stmt.setString(2, DateUtils.toDatabaseDate(resolvedCompletionDate));
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
                WHERE id = 1;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setInt(1, intervalDays);
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