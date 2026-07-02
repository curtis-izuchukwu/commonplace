package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.pararepilot.model.ConfidenceLevel;
import com.pararepilot.model.ImportanceLevel;
import com.pararepilot.model.StudyModule;
import com.pararepilot.model.Topic;
import com.pararepilot.model.Worksheet;
import com.pararepilot.service.ModuleTopicService;
import com.pararepilot.service.WorksheetRecommendation;
import com.pararepilot.service.WorksheetSelectionService;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class ModulesController {

    @FXML private TextField moduleNameField;
    @FXML private TextArea moduleDescriptionArea;
    @FXML private DatePicker moduleExamDatePicker;
    @FXML private ComboBox<ImportanceLevel> moduleImportanceCombo;

    @FXML private VBox modulesList;

    @FXML private Label recommendationTitleLabel;
    @FXML private Label recommendationMetaLabel;
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
    private final WorksheetSelectionService worksheetSelectionService = new WorksheetSelectionService();

    private StudyModule selectedModule;
    private WorksheetRecommendation currentRecommendation;

    @FXML
    private void initialize() {
        moduleImportanceCombo.getItems().setAll(ImportanceLevel.values());
        moduleImportanceCombo.setValue(ImportanceLevel.MEDIUM);

        topicImportanceCombo.getItems().setAll(ImportanceLevel.values());
        topicImportanceCombo.setValue(ImportanceLevel.MEDIUM);

        topicConfidenceCombo.getItems().setAll(ConfidenceLevel.values());
        topicConfidenceCombo.setValue(ConfidenceLevel.MEDIUM);

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

            clearModuleForm();
            selectedModule = createdModule;

            loadModules();
            selectModule(createdModule);

            setStatus("Module created: " + createdModule.name());

        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to create module", e.getMessage());
        }
    }

    @FXML
    private void handleAddTopic() {
        if (selectedModule == null) {
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

            clearTopicForm();
            loadTopicsForSelectedModule();
            loadModules();

            setStatus("Topic created: " + createdTopic.name());

        } catch (IllegalArgumentException e) {
            setStatus(e.getMessage());
        } catch (SQLException e) {
            showError("Failed to create topic", e.getMessage());
        }
    }

    @FXML
    private void handleRefreshRecommendation() {
        loadRecommendation();
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

    private void loadRecommendation() {
        try {
            var recommendation = worksheetSelectionService.recommendWorksheet();

            if (recommendation.isEmpty()) {
                currentRecommendation = null;

                recommendationTitleLabel.setText("No recommendation yet");
                recommendationMetaLabel.setText("Create worksheets to unlock adaptive recommendations.");
                recommendationReasonLabel.setText("");
                openRecommendationButton.setDisable(true);

                return;
            }

            currentRecommendation = recommendation.get();

            Worksheet worksheet = currentRecommendation.worksheet();

            recommendationTitleLabel.setText(worksheet.title());

            recommendationMetaLabel.setText(
                    "Topic: " + currentRecommendation.topic().name()
                            + " • Difficulty: " + worksheet.difficulty()
                            + " • Importance: " + worksheet.importance()
                            + " • Latest score: " + formatScore(worksheet.latestScorePercent())
            );

            recommendationReasonLabel.setText(currentRecommendation.explanation());
            openRecommendationButton.setDisable(false);

        } catch (SQLException e) {
            currentRecommendation = null;

            recommendationTitleLabel.setText("Recommendation failed");
            recommendationMetaLabel.setText(e.getMessage());
            recommendationReasonLabel.setText("");
            openRecommendationButton.setDisable(true);
        }
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
                            + " • Importance: " + module.importance()
                            + " • Topics: " + topicCount
                            + " • Average mastery: " + String.format("%.0f%%", averageMastery)
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

    private VBox createModuleCard(StudyModule module) {
        VBox card = new VBox(8);
        card.getStyleClass().add("entity-card");

        Label title = new Label(module.name());
        title.getStyleClass().add("card-title");

        Label meta = new Label(buildModuleMeta(module));
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        Button openButton = new Button("Open");
        openButton.setOnAction(event -> selectModule(module));

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event -> deleteModule(module));

        HBox actions = new HBox(8, openButton, deleteButton);

        card.getChildren().addAll(title, meta, actions);
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
                    + " • " + module.importance()
                    + " • " + topicCount + " topics"
                    + " • " + String.format("%.0f%% mastery", averageMastery);

        } catch (SQLException e) {
            return module.importance().toString();
        }
    }

    private HBox createTopicCard(Topic topic) {
        HBox card = new HBox(12);
        card.getStyleClass().add("entity-card");

        VBox textBox = new VBox(4);
        HBox.setHgrow(textBox, Priority.ALWAYS);

        Label title = new Label(topic.name());
        title.getStyleClass().add("card-title");

        Label meta = new Label(
                "Importance: " + topic.importance()
                        + " • Confidence: " + topic.confidence()
                        + " • Mastery: " + String.format("%.0f%%", topic.masteryScore())
        );
        meta.getStyleClass().add("muted-text");
        meta.setWrapText(true);

        textBox.getChildren().addAll(title, meta);

        Button viewButton = new Button("View");
        viewButton.setOnAction(event -> openTopicDetail(topic));

        Button deleteButton = new Button("Delete");
        deleteButton.getStyleClass().add("danger-button");
        deleteButton.setOnAction(event -> deleteTopic(topic));

        card.getChildren().addAll(textBox, viewButton, deleteButton);

        return card;
    }

    private void openTopicDetail(Topic topic) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/TopicDetailView.fxml")
            );

            Parent root = loader.load();

            TopicDetailController controller = loader.getController();
            controller.setTopic(topic, selectedModule);

            Stage stage = new Stage();
            stage.setTitle("Topic Details - " + topic.name());
            stage.initModality(Modality.APPLICATION_MODAL);
            Scene scene = new Scene(root, 760, 680);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();
            loadRecommendation();

        } catch (IOException e) {
            showError("Failed to open topic detail", e.getMessage());
        }
    }

    private void openWorksheetDetail(Worksheet worksheet, Topic topic) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/WorksheetDetailView.fxml")
            );

            Parent root = loader.load();

            WorksheetDetailController controller = loader.getController();
            controller.setWorksheet(worksheet, topic);

            Stage stage = new Stage();
            stage.setTitle("Worksheet Details - " + worksheet.title());
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 760, 680);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();

            loadRecommendation();

        } catch (IOException e) {
            showError("Failed to open worksheet detail", e.getMessage());
        }
    }

    private void deleteModule(StudyModule module) {
        try {
            service.deleteModule(module.id());

            if (selectedModule != null && selectedModule.id() == module.id()) {
                selectedModule = null;
                selectedModuleTitle.setText("Select a module");
                selectedModuleMeta.setText("Create or select a module to start adding topics.");
                topicsList.getChildren().clear();
            }

            loadModules();
            setStatus("Module deleted: " + module.name());

        } catch (SQLException e) {
            showError("Failed to delete module", e.getMessage());
        }
    }

    private void deleteTopic(Topic topic) {
        try {
            service.deleteTopic(topic.id());

            loadTopicsForSelectedModule();
            loadModules();

            setStatus("Topic deleted: " + topic.name());

        } catch (SQLException e) {
            showError("Failed to delete topic", e.getMessage());
        }
    }

    private void clearModuleForm() {
        moduleNameField.clear();
        moduleDescriptionArea.clear();
        moduleExamDatePicker.setValue(null);
        moduleImportanceCombo.setValue(ImportanceLevel.MEDIUM);
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

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message);
        alert.showAndWait();
    }
    @FXML
    private void handleOpenMistakeBank() {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/com/pararepilot/fxml/MistakeBankView.fxml")
            );

            Parent root = loader.load();

            Stage stage = new Stage();
            stage.setTitle("Mistake Bank");
            stage.initModality(Modality.APPLICATION_MODAL);

            Scene scene = new Scene(root, 860, 740);
            scene.getStylesheets().add(
                    getClass().getResource("/com/pararepilot/css/app.css").toExternalForm()
            );

            stage.setScene(scene);
            stage.showAndWait();

            loadRecommendation();

        } catch (IOException e) {
            showError("Failed to open mistake bank", e.getMessage());
        }
    }
}
