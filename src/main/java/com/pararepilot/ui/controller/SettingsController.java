package com.pararepilot.ui.controller;

import java.io.IOException;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import com.pararepilot.model.DifficultyLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.UserSettings;
import com.pararepilot.repository.UserStatsRepository;
import com.pararepilot.service.DataManagementService;
import com.pararepilot.service.UserSettingsService;
import com.pararepilot.ui.AppIcon;
import com.pararepilot.ui.AppPreferences;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

public class SettingsController {

    @FXML private VBox settingsRoot;
    @FXML private ComboBox<String> themeComboBox;
    @FXML private ComboBox<String> accentComboBox;
    @FXML private CheckBox reduceMotionCheckBox;
    @FXML private CheckBox compactLayoutCheckBox;
    @FXML private ComboBox<String> fontSizeComboBox;
    @FXML private Spinner<Integer> intervalDaysSpinner;
    @FXML private Spinner<Integer> dailyGoalSpinner;
    @FXML private TextField reminderTimeField;
    @FXML private ComboBox<DifficultyLevel> minDifficultyComboBox;
    @FXML private ComboBox<DifficultyLevel> maxDifficultyComboBox;
    @FXML private ComboBox<String> recommendationFocusComboBox;
    @FXML private CheckBox includeResolvedMistakesCheckBox;
    @FXML private CheckBox dailyReminderCheckBox;
    @FXML private CheckBox examReminderCheckBox;
    @FXML private CheckBox mistakeReminderCheckBox;
    @FXML private CheckBox streakReminderCheckBox;
    @FXML private CheckBox quietHoursCheckBox;
    @FXML private TextField quietHoursStartField;
    @FXML private TextField quietHoursEndField;
    @FXML private CheckBox showXpAndRankCheckBox;
    @FXML private CheckBox streakTrackingCheckBox;
    @FXML private CheckBox completionCelebrationsCheckBox;
    @FXML private ComboBox<ImportanceLevel> defaultModulePriorityComboBox;
    @FXML private CheckBox archiveCompletedModulesCheckBox;
    @FXML private CheckBox higherContrastCheckBox;
    @FXML private CheckBox largerControlsCheckBox;
    @FXML private Label statusLabel;

    private final UserSettingsService userSettingsService = new UserSettingsService();
    private final UserStatsRepository userStatsRepository = new UserStatsRepository();
    private final DataManagementService dataManagementService = new DataManagementService();

    private Runnable onSettingsSaved;
    private Runnable onSwitchAccount;

    @FXML
    private void initialize() {
        themeComboBox.getItems().setAll("Dark", "Light", "System");
        accentComboBox.getItems().setAll("Brass", "Graphite", "Forest", "Burgundy");
        fontSizeComboBox.getItems().setAll("Small", "Default", "Large");
        minDifficultyComboBox.getItems().setAll(DifficultyLevel.values());
        maxDifficultyComboBox.getItems().setAll(DifficultyLevel.values());
        recommendationFocusComboBox.getItems().setAll("Balanced", "Weak Topics", "Upcoming Exams");
        defaultModulePriorityComboBox.getItems().setAll(ImportanceLevel.values());
        intervalDaysSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 30, 1));
        dailyGoalSpinner.setValueFactory(new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 12, 1));

        loadSettings();
    }

    public void setOnSettingsSaved(Runnable onSettingsSaved) {
        this.onSettingsSaved = onSettingsSaved;
    }

    public void setOnSwitchAccount(Runnable onSwitchAccount) {
        this.onSwitchAccount = onSwitchAccount;
    }

    @FXML
    private void handleSave() {
        try {
            LocalTime reminderTime = LocalTime.parse(reminderTimeField.getText().trim());
            LocalTime quietStart = LocalTime.parse(quietHoursStartField.getText().trim());
            LocalTime quietEnd = LocalTime.parse(quietHoursEndField.getText().trim());

            UserSettings savedSettings = userSettingsService.save(new UserSettings(
                    toSettingValue(themeComboBox.getValue()),
                    toSettingValue(accentComboBox.getValue()),
                    reduceMotionCheckBox.isSelected(),
                    compactLayoutCheckBox.isSelected(),
                    toSettingValue(fontSizeComboBox.getValue()),
                    dailyGoalSpinner.getValue(),
                    reminderTime.toString(),
                    minDifficultyComboBox.getValue().name(),
                    maxDifficultyComboBox.getValue().name(),
                    toSettingValue(recommendationFocusComboBox.getValue()),
                    includeResolvedMistakesCheckBox.isSelected(),
                    dailyReminderCheckBox.isSelected(),
                    examReminderCheckBox.isSelected(),
                    mistakeReminderCheckBox.isSelected(),
                    streakReminderCheckBox.isSelected(),
                    quietHoursCheckBox.isSelected(),
                    quietStart.toString(),
                    quietEnd.toString(),
                    showXpAndRankCheckBox.isSelected(),
                    streakTrackingCheckBox.isSelected(),
                    completionCelebrationsCheckBox.isSelected(),
                    defaultModulePriorityComboBox.getValue().name(),
                    archiveCompletedModulesCheckBox.isSelected(),
                    higherContrastCheckBox.isSelected(),
                    largerControlsCheckBox.isSelected(),
                    false,
                    false
            ));

            userStatsRepository.updateWorksheetIntervalDays(intervalDaysSpinner.getValue());
            AppPreferences.apply(settingsRoot.getScene(), savedSettings);

            if (onSettingsSaved != null) {
                onSettingsSaved.run();
            }

            statusLabel.setText("Settings saved.");
            UiAnimations.validationSuccess(settingsRoot);

        } catch (DateTimeParseException e) {
            UiAnimations.validationError(reminderTimeField, quietHoursStartField, quietHoursEndField);
            statusLabel.setText("Times must use HH:mm format, for example 18:00.");
        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(settingsRoot);
            statusLabel.setText(e.getMessage());
        } catch (SQLException e) {
            statusLabel.setText("Settings failed to save: " + e.getMessage());
        }
    }

    @FXML
    private void handleChangePassword() {
        try {
            OverlayService.open(settingsRoot, "/com/pararepilot/fxml/ChangePasswordView.fxml", 520, 430);
        } catch (IOException e) {
            statusLabel.setText("Password panel failed to open: " + e.getMessage());
        }
    }

    @FXML
    private void handleSwitchAccount() {
        if (onSwitchAccount != null) {
            onSwitchAccount.run();
        }
    }

    @FXML
    private void handleLogOut() {
        handleSwitchAccount();
    }

    @FXML
    private void handleResetRecommendation() {
        try {
            userStatsRepository.resetTodaysRecommendationWindow();
            runSavedCallback();
            statusLabel.setText("Today's recommendation was reset.");
        } catch (SQLException e) {
            statusLabel.setText("Recommendation reset failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleResetGamification() {
        if (!confirm("Reset gamification progress?", "XP, rank progress, and streak progress will be reset.")) {
            return;
        }

        try {
            userStatsRepository.resetGamificationProgress();
            runSavedCallback();
            statusLabel.setText("Gamification progress reset.");
        } catch (SQLException e) {
            statusLabel.setText("Gamification reset failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleExportData() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export PararePilot Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite backup", "*.db"));
        chooser.setInitialFileName("pararepilot-backup.db");

        var file = chooser.showSaveDialog(settingsRoot.getScene().getWindow());

        if (file == null) {
            return;
        }

        try {
            dataManagementService.exportDatabase(file.toPath());
            statusLabel.setText("Backup exported to " + file.getName() + ".");
        } catch (IOException e) {
            statusLabel.setText("Export failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleImportBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import PararePilot Backup");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("SQLite backup", "*.db"));

        var file = chooser.showOpenDialog(settingsRoot.getScene().getWindow());

        if (file == null) {
            return;
        }

        if (!confirm("Import backup?", "The current local database will be replaced and you will return to sign in.")) {
            return;
        }

        try {
            dataManagementService.importDatabase(Path.of(file.toURI()));
            handleSwitchAccount();
        } catch (IOException e) {
            statusLabel.setText("Import failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleClearLocalData() {
        if (!confirm("Clear this account's local data?", "Modules, topics, worksheets, attempts, and mistakes for this account will be removed.")) {
            return;
        }

        try {
            dataManagementService.clearCurrentAccountData();
            runSavedCallback();
            statusLabel.setText("Local data cleared for this account.");
        } catch (SQLException e) {
            statusLabel.setText("Clear local data failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        OverlayService.closeFrom(settingsRoot);
    }

    private void loadSettings() {
        try {
            UserSettings settings = userSettingsService.load();
            var stats = userStatsRepository.find();

            themeComboBox.setValue(toDisplayValue(settings.theme()));
            accentComboBox.setValue(AppPreferences.accentDisplayName(settings.accentColor()));
            reduceMotionCheckBox.setSelected(settings.reduceMotion());
            compactLayoutCheckBox.setSelected(settings.compactLayout());
            fontSizeComboBox.setValue(toDisplayValue(settings.fontSize()));
            intervalDaysSpinner.getValueFactory().setValue(stats.worksheetIntervalDays());
            dailyGoalSpinner.getValueFactory().setValue(settings.dailyWorksheetGoal());
            reminderTimeField.setText(settings.dailyReminderTime());
            minDifficultyComboBox.setValue(DifficultyLevel.valueOf(settings.preferredMinDifficulty()));
            maxDifficultyComboBox.setValue(DifficultyLevel.valueOf(settings.preferredMaxDifficulty()));
            recommendationFocusComboBox.setValue(toDisplayValue(settings.recommendationFocus()));
            includeResolvedMistakesCheckBox.setSelected(settings.includeResolvedMistakesInRecommendations());
            dailyReminderCheckBox.setSelected(settings.dailyReminderEnabled());
            examReminderCheckBox.setSelected(settings.examReminderEnabled());
            mistakeReminderCheckBox.setSelected(settings.mistakeReminderEnabled());
            streakReminderCheckBox.setSelected(settings.streakReminderEnabled());
            quietHoursCheckBox.setSelected(settings.quietHoursEnabled());
            quietHoursStartField.setText(settings.quietHoursStart());
            quietHoursEndField.setText(settings.quietHoursEnd());
            showXpAndRankCheckBox.setSelected(settings.showXpAndRank());
            streakTrackingCheckBox.setSelected(settings.streakTrackingEnabled());
            completionCelebrationsCheckBox.setSelected(settings.completionCelebrationsEnabled());
            defaultModulePriorityComboBox.setValue(ImportanceLevel.valueOf(settings.defaultModulePriority()));
            archiveCompletedModulesCheckBox.setSelected(settings.archiveCompletedModules());
            higherContrastCheckBox.setSelected(settings.higherContrast());
            largerControlsCheckBox.setSelected(settings.largerControls());
            statusLabel.setText("");

        } catch (SQLException e) {
            statusLabel.setText("Settings failed to load: " + e.getMessage());
        }
    }

    private String toSettingValue(String displayValue) {
        if (displayValue == null || displayValue.isBlank()) {
            return "DARK";
        }

        return displayValue.trim().toUpperCase().replace(" ", "_");
    }

    private String toDisplayValue(String settingValue) {
        if (settingValue == null || settingValue.isBlank()) {
            return "Dark";
        }

        String lowerValue = settingValue.toLowerCase();
        String displayValue = lowerValue.replace("_", " ");
        String[] words = displayValue.split(" ");
        StringBuilder builder = new StringBuilder();

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            if (!builder.isEmpty()) {
                builder.append(" ");
            }

            builder.append(Character.toUpperCase(word.charAt(0)))
                    .append(word.substring(1));
        }

        return builder.toString();
    }

    private void runSavedCallback() {
        if (onSettingsSaved != null) {
            onSettingsSaved.run();
        }
    }

    private boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        AppIcon.applyTo(alert);

        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }
}
