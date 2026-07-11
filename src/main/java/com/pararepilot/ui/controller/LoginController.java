package com.pararepilot.ui.controller;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

import com.pararepilot.service.AccountService;
import com.pararepilot.ui.AppChrome;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {

    @FXML private Label modeLabel;
    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Button loginButton;
    @FXML private Button createAccountButton;
    @FXML private Label statusLabel;

    private final AccountService accountService = new AccountService();

    private boolean createMode;
    private boolean hasAccounts;

    @FXML
    private void initialize() {
        try {
            hasAccounts = accountService.hasAccounts();
            setCreateMode(!hasAccounts);
        } catch (SQLException e) {
            setStatus("Account database failed to load: " + e.getMessage());
            setCreateMode(true);
        }

        Platform.runLater(this::registerChromeCommands);
    }

    @FXML
    private void handleLogin() {
        if (createMode && hasAccounts) {
            setCreateMode(false);
            setStatus("");
            return;
        }

        try {
            accountService.login(
                    usernameField.getText(),
                    passwordField.getText().toCharArray()
            );

            openDashboard();

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(usernameField, passwordField);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            setStatus("Login failed: " + e.getMessage());
        } catch (IOException e) {
            setStatus("Dashboard failed to open: " + e.getMessage());
        }
    }

    @FXML
    private void handleCreateAccount() {
        if (!createMode) {
            setCreateMode(true);
            setStatus("Choose a username and confirm your new password.");
            return;
        }

        try {
            accountService.createAccount(
                    usernameField.getText(),
                    passwordField.getText().toCharArray(),
                    confirmPasswordField.getText().toCharArray()
            );

            openDashboard();

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(usernameField, passwordField, confirmPasswordField);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            setStatus("Account creation failed: " + e.getMessage());
        } catch (IOException e) {
            setStatus("Dashboard failed to open: " + e.getMessage());
        }
    }

    private void setCreateMode(boolean createMode) {
        this.createMode = createMode;

        modeLabel.setText(createMode ? "Create your account" : "Sign in to continue");
        confirmPasswordField.setVisible(createMode);
        confirmPasswordField.setManaged(createMode);
        loginButton.setDisable(createMode && !hasAccounts);
        loginButton.setText(createMode && hasAccounts ? "Back to Sign In" : "Sign In");
        createAccountButton.setText(createMode ? "Create Account" : "New Account");
    }

    private void registerChromeCommands() {
        if (usernameField.getScene() == null) {
            return;
        }

        AppChrome.setCommands(
                usernameField.getScene(),
                List.of(
                        new AppChrome.Command(
                                "Sign In",
                                "Focus the sign-in form.",
                                "login account username password",
                                () -> {
                                    if (hasAccounts) {
                                        setCreateMode(false);
                                    }
                                    usernameField.requestFocus();
                                }
                        ),
                        new AppChrome.Command(
                                "Create Account",
                                "Switch to account creation.",
                                "register new account signup",
                                () -> {
                                    setCreateMode(true);
                                    usernameField.requestFocus();
                                }
                        )
                )
        );
    }

    private void openDashboard() throws IOException {
        FXMLLoader loader = new FXMLLoader(
                getClass().getResource("/com/pararepilot/fxml/DashboardView.fxml")
        );

        Parent root = loader.load();
        UiAnimations.installGlobalAnimations(root);
        Scene scene = usernameField.getScene();
        AppChrome.setContent(scene, root);
        AppChrome.setBreadcrumb(scene, "Dashboard");
        AppChrome.setDailyChip(scene, "");

        Stage stage = (Stage) scene.getWindow();
        stage.setTitle("ParārePilot");
        stage.setMinWidth(1000);
        stage.setMinHeight(680);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }
}
