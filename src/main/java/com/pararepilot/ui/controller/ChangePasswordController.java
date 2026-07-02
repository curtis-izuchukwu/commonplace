package com.pararepilot.ui.controller;

import java.sql.SQLException;

import com.pararepilot.service.AccountService;
import com.pararepilot.ui.OverlayService;
import com.pararepilot.ui.UiAnimations;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;

public class ChangePasswordController {

    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label statusLabel;

    private final AccountService accountService = new AccountService();

    @FXML
    private void handleSave() {
        try {
            accountService.changePassword(
                    currentPasswordField.getText().toCharArray(),
                    newPasswordField.getText().toCharArray(),
                    confirmPasswordField.getText().toCharArray()
            );

            UiAnimations.validationSuccess(currentPasswordField, newPasswordField, confirmPasswordField);
            closeWindow();

        } catch (IllegalArgumentException e) {
            UiAnimations.validationError(currentPasswordField, newPasswordField, confirmPasswordField);
            setStatus(e.getMessage());
        } catch (SQLException e) {
            UiAnimations.validationError(currentPasswordField, newPasswordField, confirmPasswordField);
            setStatus("Password change failed: " + e.getMessage());
        }
    }

    @FXML
    private void handleCancel() {
        closeWindow();
    }

    private void closeWindow() {
        OverlayService.closeFrom(statusLabel);
    }

    private void setStatus(String message) {
        statusLabel.setText(message);
    }
}
