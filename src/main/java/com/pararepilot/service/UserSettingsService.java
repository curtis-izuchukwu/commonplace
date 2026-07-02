package com.pararepilot.service;

import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.UserSettings;
import com.pararepilot.repository.UserSettingsRepository;

public class UserSettingsService {

    private final UserSettingsRepository userSettingsRepository;

    public UserSettingsService() {
        this(new UserSettingsRepository());
    }

    public UserSettingsService(UserSettingsRepository userSettingsRepository) {
        this.userSettingsRepository = userSettingsRepository;
    }

    public UserSettings load() throws SQLException {
        return userSettingsRepository.find();
    }

    public UserSettings save(UserSettings settings) throws SQLException {
        validate(settings);
        return userSettingsRepository.save(settings);
    }

    private void validate(UserSettings settings) {
        if (settings.dailyWorksheetGoal() < 1) {
            throw new IllegalArgumentException("Daily worksheet goal must be at least 1.");
        }

        if (settings.dailyReminderTime() == null
                || !settings.dailyReminderTime().matches("\\d{2}:\\d{2}")) {
            throw new IllegalArgumentException("Reminder time must use HH:mm format.");
        }

        parseTime(settings.dailyReminderTime(), "Daily worksheet time");
        parseTime(settings.quietHoursStart(), "Quiet hours start");
        parseTime(settings.quietHoursEnd(), "Quiet hours end");

        DifficultyLevel minDifficulty = DifficultyLevel.valueOf(settings.preferredMinDifficulty());
        DifficultyLevel maxDifficulty = DifficultyLevel.valueOf(settings.preferredMaxDifficulty());

        if (minDifficulty.ordinal() > maxDifficulty.ordinal()) {
            throw new IllegalArgumentException("Minimum difficulty cannot be higher than maximum difficulty.");
        }
    }

    private void parseTime(String value, String label) {
        try {
            LocalTime.parse(value);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException(label + " must use HH:mm format.");
        }
    }
}
