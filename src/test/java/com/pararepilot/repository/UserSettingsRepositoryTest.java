package com.pararepilot.repository;

import com.pararepilot.model.UserSettings;
import com.pararepilot.service.UserSettingsService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserSettingsRepositoryTest {
    @Test
    void savesAndReloadsEveryAccentWithoutFallingBackToCyan() throws Exception {
        UserSettingsService service = new UserSettingsService();
        UserSettings original = service.load();
        try {
            for (String accent : List.of("BRASS", "GRAPHITE", "FOREST", "BURGUNDY", "CYAN", "BLUE", "MINT", "ROSE")) {
                UserSettings changed = withAccent(original, accent);
                assertEquals(accent, service.save(changed).accentColor(), "Save should retain " + accent);
                assertEquals(changed, new UserSettingsService().load(), "Reopening settings should retain " + accent);
            }
        } finally {
            service.save(original);
        }
    }

    private UserSettings withAccent(UserSettings settings, String accent) {
        return new UserSettings(
                settings.theme(), accent, settings.reduceMotion(), settings.compactLayout(), settings.fontSize(),
                settings.dailyWorksheetGoal(), settings.dailyReminderTime(), settings.preferredMinDifficulty(),
                settings.preferredMaxDifficulty(), settings.recommendationFocus(),
                settings.includeResolvedMistakesInRecommendations(), settings.dailyReminderEnabled(),
                settings.examReminderEnabled(), settings.mistakeReminderEnabled(), settings.streakReminderEnabled(),
                settings.quietHoursEnabled(), settings.quietHoursStart(), settings.quietHoursEnd(),
                settings.showXpAndRank(), settings.streakTrackingEnabled(), settings.completionCelebrationsEnabled(),
                settings.defaultModulePriority(), settings.archiveCompletedModules(), settings.higherContrast(),
                settings.largerControls(), settings.keyboardHintsEnabled(), settings.screenReaderLabelsEnabled()
        );
    }
}
