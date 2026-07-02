package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.UserStats;
import com.pararepilot.model.Worksheet;
import com.pararepilot.service.DashboardService;
import com.pararepilot.service.DashboardSummary;
import com.pararepilot.service.ModuleTopicService;
import com.pararepilot.service.UserSettingsService;
import com.pararepilot.service.WorksheetRecommendation;
import com.pararepilot.ui.LevelUi;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

public class ModulesController {

    @FXML private TextField moduleNameField;
    @FXML private TextArea moduleDescriptionArea;
    @FXML private DatePicker moduleExamDatePicker;
    @FXML private ComboBox<ImportanceLevel> moduleImportanceCombo;

    @FXML private VBox modulesList;

    @FXML private Label recommendationTitleLabel;
    @FXML private Label recommendationMetaLabel;
    @FXML private HBox recommendationVisualMeta;
    @FXML private Label recommendationReasonLabel;
    @FXML private Button openRecommendationButton;

    @FXML private Label selectedModuleTitle;
    @FXML private Label selectedModuleMeta;

    @FXML private TextField topicNameField;
    @FXML private TextArea topicDescriptionArea;
    @FXML private ComboBox<ImportanceLevel> topicImportanceCombo;
    @FXML private ComboBox<ConfidenceLevel> topicConfidenceCombo;

    @FXML private VBox topicsList;
    @FXML private Label statusLabel;

    private final ModuleTopicService service = new ModuleTopicService();
    private final DashboardService dashboardService = new DashboardService();
    private final UserSettingsService userSettingsService = new UserSettingsService();

    private StudyModule selectedModule;
    private WorksheetRecommendation currentRecommendation;

    @FXML
    private void initialize() {
        moduleImportanceCombo.getItems().setAll(ImportanceLevel.values());
        moduleImportanceCombo.setValue(defaultModulePriority());
        LevelUi.applyLevelBarStyling(moduleImportanceCombo);

        topicImportanceCombo.getItems().setAll(ImportanceLevel.values());
        topicImportanceCombo.setValue(ImportanceLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(topicImportanceCombo);

        topicConfidenceCombo.getItems().setAll(ConfidenceLevel.values());
        topicConfidenceCombo.setValue(ConfidenceLevel.MEDIUM);
        LevelUi.applyLevelBarStyling(topicConfidenceCombo);

        loadModules();
        loadRecommendation();
    }

    @FXML
    private void handleAddModule() {
        try {
            StudyModule createdModule = service.createModule(
                    moduleNameField.getText(),
                    moduleDescriptionArea.getText(),
                    moduleExamDatePicker.getValue(),
                    moduleImportanceCombo.getValue()
            );

            UiAnimations.validationSuccess(moduleNameField, moduleDescriptionArea, moduleExamDatePicker);
            clearModuleForm();
            selectedModule = createdModule;

            loadModules();
            selectModule(createdModule);
            loadRecommendation();

            setStatus("Module created: " + createdModule.name());

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(moduleNameField);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to create module", e.getMessage());
        }
    }

    @FXML
    private void handleAddTopic() {
        if (selectedModule == null) {
            UiAnimations.validationError(modulesList);
            setStatus("Select a module before adding a topic.");
            return;
        }

        try {
            Topic createdTopic = service.createTopic(
                    selectedModule.id(),
                    topicNameField.getText(),
                    topicDescriptionArea.getText(),
                    topicImportanceCombo.getValue(),
                    topicConfidenceCombo.getValue()
            );

            UiAnimations.validationSuccess(topicNameField, topicDescriptionArea);
            clearTopicForm();
            loadTopicsForSelectedModule();
            loadModules();
            loadRecommendation();

            setStatus("Topic created: " + createdTopic.name());

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(topicNameField);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to create topic", e.getMessage());
        }
    }

    @FXML
    private void handleRefreshRecommendation() {
        UiAnimations.animateRecommendationRefresh(recommendationTitleLabel.getParent(), this::refreshRecommendation);
    }

    @FXML
    private void handleOpenRecommendedWorksheet() {
        if (currentRecommendation == null) {
            setStatus("No recommended worksheet is available yet.");
            return;
        }

        openWorksheetDetail(
                currentRecommendation.worksheet(),
                currentRecommendation.topic()
        );
    }

    @FXML
    private void handleOpenMistakeBank() {
        try {
            var handle = OverlayService.<MistakeBankController>open(
                    statusLabel,
                    "/com/pararepilot/fxml/MistakeBankView.fxml",
                    920,
                    760
            );
            handle.controller().setOnMistakesChanged(this::refreshSelectedModuleData);

        } catch (IOException e) {
            showError("Failed to open mistake bank", e.getMessage());
        }
    }

    private void loadRecommendation() {
        try {
            updateRecommendation(dashboardService.loadDashboard());

        } catch (SQLException e) {
            currentRecommendation = null;
            recommendationVisualMeta.getChildren().clear();

            UiAnimations.fadeTextChange(recommendationTitleLabel, "Recommendation failed");
            UiAnimations.fadeTextChange(recommendationMetaLabel, e.getMessage());
            setRecommendationReason("");
            openRecommendationButton.setDisable(true);
        }
    }

    private void refreshRecommendation() {
        try {
            updateRecommendation(dashboardService.refreshRecommendation());
        } catch (SQLException e) {
            currentRecommendation = null;
            recommendationVisualMeta.getChildren().clear();
            UiAnimations.fadeTextChange(recommendationTitleLabel, "Recommendation failed");
            UiAnimations.fadeTextChange(recommendationMetaLabel, e.getMessage());
            setRecommendationReason("");
            openRecommendationButton.setDisable(true);
        }
    }

    private void updateRecommendation(DashboardSummary summary) {
        if (summary.worksheetWindowLocked()) {
            currentRecommendation = null;
            recommendationVisualMeta.getChildren().clear();

            Label completionBadge = new Label("Completed");
            completionBadge.getStyleClass().add("completion-badge");
            recommendationVisualMeta.getChildren().add(completionBadge);
            UiAnimations.popIn(completionBadge);

            UiAnimations.fadeTextChange(recommendationTitleLabel, "Today's worksheet is complete");
            UiAnimations.fadeTextChange(
                    recommendationMetaLabel,
                    "Next recommendation unlocks in "
                            + countdownUntilNextWorksheet(summary.userStats()) + "."
            );
            setRecommendationReason("");
            openRecommendationButton.setDisable(true);
            return;
        }

        if (summary.recommendation().isEmpty()) {
            currentRecommendation = null;
            recommendationVisualMeta.getChildren().clear();

            UiAnimations.fadeTextChange(recommendationTitleLabel, "No recommendation yet");
            UiAnimations.fadeTextChange(
                    recommendationMetaLabel,
                    "Create worksheets to unlock adaptive recommendations."
            );
            setRecommendationReason("");
            openRecommendationButton.setDisable(true);
            return;
        }

        currentRecommendation = summary.recommendation().get();

        Worksheet worksheet = currentRecommendation.worksheet();

        UiAnimations.fadeTextChange(recommendationTitleLabel, worksheet.title());

        UiAnimations.fadeTextChange(
                recommendationMetaLabel,
                "Topic: " + currentRecommendation.topic().name()
        );

        recommendationVisualMeta.getChildren().setAll(
                LevelUi.createDifficultyIndicator(worksheet.difficulty()),
                LevelUi.createPriorityChip(worksheet.importance())
        );

        setRecommendationReason("");
        openRecommendationButton.setDisable(false);
        UiAnimations.softPulse(openRecommendationButton);
    }

    private String countdownUntilNextWorksheet(UserStats stats) {
        if (stats.lastCompletionDate() == null) {
            return "a moment";
        }

        LocalDate nextWorksheetDate = stats.lastCompletionDate()
                .plusDays(stats.worksheetIntervalDays());

        LocalDateTime unlockTime = nextWorksheetDate.atStartOfDay();
        Duration duration = Duration.between(LocalDateTime.now(), unlockTime);

        if (duration.isNegative() || duration.isZero()) {
            return "a moment";
        }

        long hours = duration.toHours();
        long minutes = duration.minusHours(hours).toMinutes();

        if (hours <= 0) {
            return minutes + " minute" + (minutes == 1 ? "" : "s");
        }

        return hours + " hour" + (hours == 1 ? "" : "s")
                + " " + minutes + " minute" + (minutes == 1 ? "" : "s");
    }

    private String dailyProgressText(DashboardSummary summary) {
        int dailyGoal = summary.userSettings().dailyWorksheetGoal();
        int completed = Math.min(summary.completedWorksheetsToday(), dailyGoal);

        return completed + "/" + dailyGoal
                + " worksheet" + (dailyGoal == 1 ? "" : "s");
    }

    private void loadModules() {
        modulesList.getChildren().clear();

        try {
            List<StudyModule> modules = service.getAllModules();

            if (modules.isEmpty()) {
                Label emptyLabel = new Label("No modules yet. Create your first module.");
                emptyLabel.getStyleClass().add("muted-text");
                modulesList.getChildren().add(emptyLabel);
                return;
            }

            for (StudyModule module : modules) {
                modulesList.getChildren().add(createModuleCard(module));
            }

        } catch (SQLException e) {
            showError("Failed to load modules", e.getMessage());
        }
    }

    private void selectModule(StudyModule module) {
        selectedModule = module;

        selectedModuleTitle.setText(module.name());

        try {
            int topicCount = service.countTopicsForModule(module.id());
            double averageMastery = service.getAverageMasteryForModule(module.id());

            String examText = module.examDate() == null
                    ? "No exam date"
                    : "Exam: " + module.examDate();

            selectedModuleMeta.setText(
                    examText
                            + " - Importance: " + module.importance()
                            + " - Topics: " + topicCount
                            + " - Average mastery: " + String.format("%.0f%%", averageMastery)
            );

            loadTopicsForSelectedModule();

        } catch (SQLException e) {
            showError("Failed to load module details", e.getMessage());
        }
    }

    private void loadTopicsForSelectedModule() {
        topicsList.getChildren().clear();

        if (selectedModule == null) {
            Label emptyLabel = new Label("Select a module to view topics.");
            emptyLabel.getStyleClass().add("muted-text");
            topicsList.getChildren().add(emptyLabel);
            return;
        }

        try {
            List<Topic> topics = service.getTopicsForModule(selectedModule.id());

            if (topics.isEmpty()) {
                Label emptyLabel = new Label("No topics yet. Add one above.");
                emptyLabel.getStyleClass().add("muted-text");
                topicsList.getChildren().add(emptyLabel);
                return;
            }

            for (Topic topic : topics) {
                topicsList.getChildren().add(createTopicCard(topic));
            }

        } catch (SQLException e) {
            showError("Failed to load topics", e.getMessage());
        }
    }

    private StackPane createModuleCard(StudyModule module) {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("entity-card", "clickable-card");
        card.setOnMouseClicked(event -> selectModule(module));

        VBox content = new VBox(8);
        content.setPadding(new Insets(0, 34, 0, 0));

        Label title = new Label(module.name());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(buildModuleMeta(module));
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        Button deleteButton = createDeleteButton();
        deleteButton.setOnMouseClicked(event -> event.consume());
        deleteButton.setOnAction(event -> {
            event.consume();
            deleteModule(module, card);
        });

        content.getChildren().addAll(title, meta);
        card.getChildren().addAll(content, deleteButton);
        StackPane.setAlignment(deleteButton, Pos.TOP_RIGHT);

        UiAnimations.animateCardEntry(card);
        return card;
    }

    private String buildModuleMeta(StudyModule module) {
        try {
            int topicCount = service.countTopicsForModule(module.id());
            double averageMastery = service.getAverageMasteryForModule(module.id());

            String examText = module.examDate() == null
                    ? "No exam date"
                    : "Exam: " + module.examDate();

            return examText
                    + " - " + module.importance()
                    + " - " + topicCount + " topics"
                    + " - " + String.format("%.0f%% mastery", averageMastery);

        } catch (SQLException e) {
            return module.importance().toString();
        }
    }

    private StackPane createTopicCard(Topic topic) {
        StackPane card = new StackPane();
        card.getStyleClass().addAll("entity-card", "clickable-card");
        card.setOnMouseClicked(event -> openTopicDetail(topic));

        VBox textBox = new VBox(4);
        textBox.setPadding(new Insets(0, 34, 0, 0));

        Label title = new Label(topic.name());
        title.getStyleClass().add("card-title");
        title.setWrapText(true);

        Label meta = new Label(
                "Importance: " + topic.importance()
                        + " - Confidence: " + topic.confidence()
                        + " - Mastery: " + String.format("%.0f%%", topic.masteryScore())
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        textBox.getChildren().addAll(title, meta);

        Button deleteButton = createDeleteButton();
        deleteButton.setOnMouseClicked(event -> event.consume());
        deleteButton.setOnAction(event -> {
            event.consume();
            deleteTopic(topic, card);
        });

        card.getChildren().addAll(textBox, deleteButton);
        StackPane.setAlignment(deleteButton, Pos.TOP_RIGHT);

        UiAnimations.animateCardEntry(card);
        return card;
    }

    private Button createDeleteButton() {
        Button button = new Button("X");
        button.getStyleClass().add("icon-danger-button");
        button.setFocusTraversable(false);
        return button;
    }

    private void openTopicDetail(Topic topic) {
        try {
            var handle = OverlayService.<TopicDetailController>open(
                    statusLabel,
                    "/com/pararepilot/fxml/TopicDetailView.fxml",
                    840,
                    720
            );

            TopicDetailController controller = handle.controller();
            controller.setTopic(topic, selectedModule);
            controller.setOnDataChanged(this::refreshSelectedModuleData);

        } catch (IOException e) {
            showError("Failed to open topic detail", e.getMessage());
        }
    }

    private void openWorksheetDetail(Worksheet worksheet, Topic topic) {
        try {
            var handle = OverlayService.<WorksheetDetailController>open(
                    statusLabel,
                    "/com/pararepilot/fxml/WorksheetDetailView.fxml",
                    840,
                    720
            );

            WorksheetDetailController controller = handle.controller();
            controller.setWorksheet(worksheet, topic);
            controller.setRecommendationDetails(currentRecommendation);
            controller.setOnWorksheetUpdated(this::refreshSelectedModuleData);

        } catch (IOException e) {
            showError("Failed to open worksheet detail", e.getMessage());
        }
    }

    private void deleteModule(StudyModule module, Node card) {
        try {
            service.deleteModule(module.id());

            UiAnimations.animateCardRemoval(card, () -> {
                if (selectedModule != null && selectedModule.id() == module.id()) {
                    selectedModule = null;
                    selectedModuleTitle.setText("Select a module");
                    selectedModuleMeta.setText("Create or select a module to start adding topics.");
                    topicsList.getChildren().clear();
                }

                loadModules();
                loadRecommendation();
                setStatus("Module deleted: " + module.name());
            });

        } catch (SQLException e) {
            showError("Failed to delete module", e.getMessage());
        }
    }

    private void deleteTopic(Topic topic, Node card) {
        try {
            service.deleteTopic(topic.id());

            UiAnimations.animateCardRemoval(card, () -> {
                loadTopicsForSelectedModule();
                loadModules();
                loadRecommendation();

                setStatus("Topic deleted: " + topic.name());
            });

        } catch (SQLException e) {
            showError("Failed to delete topic", e.getMessage());
        }
    }

    private void refreshSelectedModuleData() {
        if (selectedModule != null) {
            selectModule(selectedModule);
        }

        loadModules();
        loadRecommendation();
    }

    private void clearModuleForm() {
        moduleNameField.clear();
        moduleDescriptionArea.clear();
        moduleExamDatePicker.setValue(null);
        moduleImportanceCombo.setValue(defaultModulePriority());
    }

    private void clearTopicForm() {
        topicNameField.clear();
        topicDescriptionArea.clear();
        topicImportanceCombo.setValue(ImportanceLevel.MEDIUM);
        topicConfidenceCombo.setValue(ConfidenceLevel.MEDIUM);
    }

    private String formatScore(Double score) {
        if (score == null) {
            return "Not attempted";
        }

        return String.format("%.0f%%", score);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }

    private void setRecommendationReason(String message) {
        boolean hasMessage = message != null && !message.isBlank();
        recommendationReasonLabel.setText(hasMessage ? message : "");
        recommendationReasonLabel.setVisible(hasMessage);
        recommendationReasonLabel.setManaged(hasMessage);
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private ImportanceLevel defaultModulePriority() {
        try {
            return ImportanceLevel.valueOf(userSettingsService.load().defaultModulePriority());
        } catch (SQLException | IllegalArgumentException e) {
            return ImportanceLevel.MEDIUM;
        }
    }
}
