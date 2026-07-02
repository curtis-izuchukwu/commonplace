package com.pararepilot.model;

public record UserSettings(
        String theme,
        String accentColor,
        boolean reduceMotion,
        boolean compactLayout,
        String fontSize,
        int dailyWorksheetGoal,
        String dailyReminderTime,
        String preferredMinDifficulty,
        String preferredMaxDifficulty,
        String recommendationFocus,
        boolean includeResolvedMistakesInRecommendations,
        boolean dailyReminderEnabled,
        boolean examReminderEnabled,
        boolean mistakeReminderEnabled,
        boolean streakReminderEnabled,
        boolean quietHoursEnabled,
        String quietHoursStart,
        String quietHoursEnd,
        boolean showXpAndRank,
        boolean streakTrackingEnabled,
        boolean completionCelebrationsEnabled,
        String defaultModulePriority,
        boolean archiveCompletedModules,
        boolean higherContrast,
        boolean largerControls,
        boolean keyboardHintsEnabled,
        boolean screenReaderLabelsEnabled
) {

    public static UserSettings defaults() {
        return new UserSettings(
                "DARK",
                "CYAN",
                false,
                false,
                "DEFAULT",
                1,
                "18:00",
                "EASY",
                "HARD",
                "BALANCED",
                false,
                false,
                true,
                true,
                true,
                false,
                "22:00",
                "07:00",
                true,
                true,
                true,
                "MEDIUM",
                false,
                false,
                false,
                false,
                true
        );
    }
}
