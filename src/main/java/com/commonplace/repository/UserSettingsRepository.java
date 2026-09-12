package com.commonplace.repository;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import com.commonplace.model.UserSettings;
import com.commonplace.service.AccountSession;

public class UserSettingsRepository {

    public UserSettings find() throws SQLException {
        String sql = """
                SELECT theme,
                       accent_color,
                       reduce_motion,
                       compact_layout,
                       font_size,
                       daily_worksheet_goal,
                       daily_reminder_time,
                       preferred_min_difficulty,
                       preferred_max_difficulty,
                       recommendation_focus,
                       include_resolved_mistakes_in_recommendations,
                       daily_reminder_enabled,
                       exam_reminder_enabled,
                       mistake_reminder_enabled,
                       streak_reminder_enabled,
                       quiet_hours_enabled,
                       quiet_hours_start,
                       quiet_hours_end,
                       show_xp_and_rank,
                       streak_tracking_enabled,
                       completion_celebrations_enabled,
                       default_module_priority,
                       archive_completed_modules,
                       higher_contrast,
                       larger_controls,
                       keyboard_hints_enabled,
                       screen_reader_labels_enabled
                FROM user_settings
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

        ensureSettingsRow();
        return find();
    }

    public UserSettings save(UserSettings settings) throws SQLException {
        String sql = """
                INSERT INTO user_settings (
                    user_id,
                    theme,
                    accent_color,
                    reduce_motion,
                    compact_layout,
                    font_size,
                    daily_worksheet_goal,
                    daily_reminder_time,
                    preferred_min_difficulty,
                    preferred_max_difficulty,
                    recommendation_focus,
                    include_resolved_mistakes_in_recommendations,
                    daily_reminder_enabled,
                    exam_reminder_enabled,
                    mistake_reminder_enabled,
                    streak_reminder_enabled,
                    quiet_hours_enabled,
                    quiet_hours_start,
                    quiet_hours_end,
                    show_xp_and_rank,
                    streak_tracking_enabled,
                    completion_celebrations_enabled,
                    default_module_priority,
                    archive_completed_modules,
                    higher_contrast,
                    larger_controls,
                    keyboard_hints_enabled,
                    screen_reader_labels_enabled
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT(user_id) DO UPDATE SET
                    theme = excluded.theme,
                    accent_color = excluded.accent_color,
                    reduce_motion = excluded.reduce_motion,
                    compact_layout = excluded.compact_layout,
                    font_size = excluded.font_size,
                    daily_worksheet_goal = excluded.daily_worksheet_goal,
                    daily_reminder_time = excluded.daily_reminder_time,
                    preferred_min_difficulty = excluded.preferred_min_difficulty,
                    preferred_max_difficulty = excluded.preferred_max_difficulty,
                    recommendation_focus = excluded.recommendation_focus,
                    include_resolved_mistakes_in_recommendations = excluded.include_resolved_mistakes_in_recommendations,
                    daily_reminder_enabled = excluded.daily_reminder_enabled,
                    exam_reminder_enabled = excluded.exam_reminder_enabled,
                    mistake_reminder_enabled = excluded.mistake_reminder_enabled,
                    streak_reminder_enabled = excluded.streak_reminder_enabled,
                    quiet_hours_enabled = excluded.quiet_hours_enabled,
                    quiet_hours_start = excluded.quiet_hours_start,
                    quiet_hours_end = excluded.quiet_hours_end,
                    show_xp_and_rank = excluded.show_xp_and_rank,
                    streak_tracking_enabled = excluded.streak_tracking_enabled,
                    completion_celebrations_enabled = excluded.completion_celebrations_enabled,
                    default_module_priority = excluded.default_module_priority,
                    archive_completed_modules = excluded.archive_completed_modules,
                    higher_contrast = excluded.higher_contrast,
                    larger_controls = excluded.larger_controls,
                    keyboard_hints_enabled = excluded.keyboard_hints_enabled,
                    screen_reader_labels_enabled = excluded.screen_reader_labels_enabled;
                """;

        try (Connection conn = DatabaseManager.connect();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setLong(1, AccountSession.currentUserId());
            stmt.setString(2, sanitizeTheme(settings.theme()));
            stmt.setString(3, sanitizeAccent(settings.accentColor()));
            stmt.setInt(4, settings.reduceMotion() ? 1 : 0);
            stmt.setInt(5, settings.compactLayout() ? 1 : 0);
            stmt.setString(6, sanitizeFontSize(settings.fontSize()));
            stmt.setInt(7, Math.max(1, settings.dailyWorksheetGoal()));
            stmt.setString(8, settings.dailyReminderTime());
            stmt.setString(9, sanitizeDifficulty(settings.preferredMinDifficulty()));
            stmt.setString(10, sanitizeDifficulty(settings.preferredMaxDifficulty()));
            stmt.setString(11, sanitizeRecommendationFocus(settings.recommendationFocus()));
            stmt.setInt(12, settings.includeResolvedMistakesInRecommendations() ? 1 : 0);
            stmt.setInt(13, settings.dailyReminderEnabled() ? 1 : 0);
            stmt.setInt(14, settings.examReminderEnabled() ? 1 : 0);
            stmt.setInt(15, settings.mistakeReminderEnabled() ? 1 : 0);
            stmt.setInt(16, settings.streakReminderEnabled() ? 1 : 0);
            stmt.setInt(17, settings.quietHoursEnabled() ? 1 : 0);
            stmt.setString(18, settings.quietHoursStart());
            stmt.setString(19, settings.quietHoursEnd());
            stmt.setInt(20, settings.showXpAndRank() ? 1 : 0);
            stmt.setInt(21, settings.streakTrackingEnabled() ? 1 : 0);
            stmt.setInt(22, settings.completionCelebrationsEnabled() ? 1 : 0);
            stmt.setString(23, sanitizeImportance(settings.defaultModulePriority()));
            stmt.setInt(24, settings.archiveCompletedModules() ? 1 : 0);
            stmt.setInt(25, settings.higherContrast() ? 1 : 0);
            stmt.setInt(26, settings.largerControls() ? 1 : 0);
            stmt.setInt(27, settings.keyboardHintsEnabled() ? 1 : 0);
            stmt.setInt(28, settings.screenReaderLabelsEnabled() ? 1 : 0);
            stmt.executeUpdate();
        }

        return find();
    }

    private void ensureSettingsRow() throws SQLException {
        save(UserSettings.defaults());
    }

    private UserSettings mapRow(ResultSet rs) throws SQLException {
        return new UserSettings(
                sanitizeTheme(rs.getString("theme")),
                sanitizeAccent(rs.getString("accent_color")),
                rs.getInt("reduce_motion") == 1,
                rs.getInt("compact_layout") == 1,
                sanitizeFontSize(rs.getString("font_size")),
                Math.max(1, rs.getInt("daily_worksheet_goal")),
                rs.getString("daily_reminder_time"),
                sanitizeDifficulty(rs.getString("preferred_min_difficulty")),
                sanitizeDifficulty(rs.getString("preferred_max_difficulty")),
                sanitizeRecommendationFocus(rs.getString("recommendation_focus")),
                rs.getInt("include_resolved_mistakes_in_recommendations") == 1,
                rs.getInt("daily_reminder_enabled") == 1,
                rs.getInt("exam_reminder_enabled") == 1,
                rs.getInt("mistake_reminder_enabled") == 1,
                rs.getInt("streak_reminder_enabled") == 1,
                rs.getInt("quiet_hours_enabled") == 1,
                rs.getString("quiet_hours_start"),
                rs.getString("quiet_hours_end"),
                rs.getInt("show_xp_and_rank") == 1,
                rs.getInt("streak_tracking_enabled") == 1,
                rs.getInt("completion_celebrations_enabled") == 1,
                sanitizeImportance(rs.getString("default_module_priority")),
                rs.getInt("archive_completed_modules") == 1,
                rs.getInt("higher_contrast") == 1,
                rs.getInt("larger_controls") == 1,
                rs.getInt("keyboard_hints_enabled") == 1,
                rs.getInt("screen_reader_labels_enabled") == 1
        );
    }

    private String sanitizeTheme(String theme) {
        if (theme == null || theme.isBlank()) {
            return "DARK";
        }

        return switch (theme.toUpperCase()) {
            case "LIGHT", "SYSTEM" -> theme.toUpperCase();
            default -> "DARK";
        };
    }

    private String sanitizeAccent(String accentColor) {
        if (accentColor == null || accentColor.isBlank()) {
            return "CYAN";
        }

        return switch (accentColor.toUpperCase(java.util.Locale.ROOT)) {
            case "BRASS", "GRAPHITE", "FOREST", "BURGUNDY", "BLUE", "MINT", "ROSE" ->
                    accentColor.toUpperCase(java.util.Locale.ROOT);
            default -> "CYAN";
        };
    }

    private String sanitizeDifficulty(String difficulty) {
        if (difficulty == null || difficulty.isBlank()) {
            return "MEDIUM";
        }

        return switch (difficulty.toUpperCase()) {
            case "EASY", "HARD" -> difficulty.toUpperCase();
            default -> "MEDIUM";
        };
    }

    private String sanitizeRecommendationFocus(String focus) {
        if (focus == null || focus.isBlank()) {
            return "BALANCED";
        }

        return switch (focus.toUpperCase()) {
            case "WEAK_TOPICS", "UPCOMING_EXAMS" -> focus.toUpperCase();
            default -> "BALANCED";
        };
    }

    private String sanitizeImportance(String importance) {
        if (importance == null || importance.isBlank()) {
            return "MEDIUM";
        }

        return switch (importance.toUpperCase()) {
            case "LOW", "HIGH" -> importance.toUpperCase();
            default -> "MEDIUM";
        };
    }

    private String sanitizeFontSize(String fontSize) {
        if (fontSize == null || fontSize.isBlank()) {
            return "DEFAULT";
        }

        return switch (fontSize.toUpperCase()) {
            case "SMALL", "LARGE" -> fontSize.toUpperCase();
            default -> "DEFAULT";
        };
    }
}
