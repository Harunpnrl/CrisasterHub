package com.pnrl.crisasterhub;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import java.io.IOException;

public class LoginController {

    @FXML private ToggleGroup authTabGroup;
    @FXML private ToggleButton tabSignin;
    @FXML private ToggleButton tabRegister;

    @FXML private VBox signinPane;
    @FXML private VBox registerPane;

    // Sign In components
    @FXML private ToggleGroup roleGroup;
    @FXML private TextField loginEmailField;
    @FXML private PasswordField loginPasswordField;
    @FXML private TextField loginPasswordVisibleField;
    @FXML private SVGPath eyeIcon;
    @FXML private Label loginErrorLabel;

    // Register components
    @FXML private TextField regNameField;
    @FXML private TextField regUsernameField;
    @FXML private TextField regEmailField;
    @FXML private PasswordField regPasswordField;
    @FXML private PasswordField regConfirmPasswordField;
    @FXML private SVGPath nameCheckIcon;
    @FXML private SVGPath userCheckIcon;
    @FXML private SVGPath emailCheckIcon;
    @FXML private SVGPath pwdCheckIcon;
    @FXML private SVGPath confirmCheckIcon;
    @FXML private Label registerErrorLabel;

    private boolean isPasswordVisible = false;

    private static final String EYE_OPEN_PATH = "M12 4.5C7 4.5 2.73 7.61 1 12c1.73 4.39 6 7.5 11 7.5s9.27-3.11 11-7.5c-1.73-4.39-6-7.5-11-7.5zM12 17c-2.76 0-5-2.24-5-5s2.24-5 5-5 5 2.24 5 5-2.24 5-5 5zm0-8c-1.66 0-3 1.34-3 3s1.34 3 3 3 3-1.34 3-3-1.34-3-3-3z";
    private static final String EYE_OFF_PATH = "M12 7c2.76 0 5 2.24 5 5 0 .65-.13 1.26-.36 1.83l2.92 2.92c1.51-1.26 2.7-2.89 3.43-4.75-1.73-4.39-6-7.5-11-7.5-1.4 0-2.74.25-3.98.7l2.16 2.16C10.74 7.13 11.35 7 12 7zM2 4.27l2.28 2.28.46.46C3.08 8.3 1.78 10.02 1 12c1.73 4.39 6 7.5 11 7.5 1.55 0 3.03-.3 4.38-.84l.42.42L19.73 22 21 20.73 3.27 3 2 4.27z";

    @FXML
    public void initialize() {
        System.out.println("Initializing App...");

        loginPasswordVisibleField.textProperty().bindBidirectional(loginPasswordField.textProperty());

        enforceAlwaysSelected(authTabGroup);
        enforceAlwaysSelected(roleGroup);

        regNameField.textProperty().addListener((obs, oldV, newV) ->
                nameCheckIcon.setVisible(newV != null && newV.trim().length() >= 3));

        regUsernameField.textProperty().addListener((obs, oldV, newV) ->
                userCheckIcon.setVisible(newV != null && newV.trim().length() >= 3));

        regEmailField.textProperty().addListener((obs, oldV, newV) ->
                emailCheckIcon.setVisible(newV != null && newV.trim().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")));

        regPasswordField.textProperty().addListener((obs, oldV, newV) -> {
            pwdCheckIcon.setVisible(newV != null && newV.length() >= 6);
            validateConfirmMatch();
        });

        regConfirmPasswordField.textProperty().addListener((obs, oldV, newV) -> validateConfirmMatch());
    }

    private void enforceAlwaysSelected(ToggleGroup group) {
        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle == null && oldToggle != null) {
                oldToggle.setSelected(true);
            }
        });
    }

    private void validateConfirmMatch() {
        String pwd = regPasswordField.getText();
        String confirm = regConfirmPasswordField.getText();
        confirmCheckIcon.setVisible(pwd != null && pwd.length() >= 6 && pwd.equals(confirm));
    }

    @FXML
    private void handleSwitchToSignIn() {
        hideError(loginErrorLabel);
        signinPane.setVisible(true);
        signinPane.setManaged(true);
        registerPane.setVisible(false);
        registerPane.setManaged(false);
    }

    @FXML
    private void handleSwitchToRegister() {
        hideError(registerErrorLabel);
        registerPane.setVisible(true);
        registerPane.setManaged(true);
        signinPane.setVisible(false);
        signinPane.setManaged(false);
    }

    @FXML
    private void handleTogglePasswordVisibility() {
        isPasswordVisible = !isPasswordVisible;
        loginPasswordVisibleField.setVisible(isPasswordVisible);
        loginPasswordVisibleField.setManaged(isPasswordVisible);
        loginPasswordField.setVisible(!isPasswordVisible);
        loginPasswordField.setManaged(!isPasswordVisible);
        eyeIcon.setContent(isPasswordVisible ? EYE_OFF_PATH : EYE_OPEN_PATH);
    }

    @FXML
    private void handleLoginSubmit(ActionEvent event) {
        hideError(loginErrorLabel);

        String identifier = loginEmailField.getText().trim();
        String password = loginPasswordField.getText();

        if (identifier.isEmpty() || password.isEmpty()) {
            showInlineError(loginErrorLabel, "Username/email and password are required.");
            showAlert("Error", "Username/email and password are required.");
            return;
        }

        ToggleButton selectedBtn = (ToggleButton) roleGroup.getSelectedToggle();
        String requestedRole = selectedBtn != null ? selectedBtn.getText() : "Victim";

        authenticateAndNavigate(event, identifier, password, requestedRole, loginErrorLabel);
    }

    @FXML
    private void handleRegisterSubmit(ActionEvent event) {
        hideError(registerErrorLabel);

        String fullName = regNameField.getText().trim();
        String username = regUsernameField.getText().trim();
        String email = regEmailField.getText().trim();
        String password = regPasswordField.getText();
        String confirmPassword = regConfirmPasswordField.getText();

        if (fullName.length() < 3 || username.length() < 3) {
            showInlineError(registerErrorLabel, "Full name and username must be at least 3 characters.");
            return;
        }

        if (!email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            showInlineError(registerErrorLabel, "Please enter a valid email address.");
            return;
        }

        if (password.length() < 6) {
            showInlineError(registerErrorLabel, "Password must be at least 6 characters.");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showInlineError(registerErrorLabel, "Passwords do not match.");
            return;
        }

        AuthService.UserSession session =
                AuthService.registerAccount(fullName, username, email, password);
        if (session == null) {
            String msg = "Registration failed. This email may already be registered.";
            showInlineError(registerErrorLabel, msg);
            showAlert("Registration Failed", msg);
            return;
        }

        navigateToDashboard(event, session);
    }

    private void authenticateAndNavigate(ActionEvent event, String identifier, String password, String requestedRole, Label targetErrorLabel) {
        AuthService.UserSession session = AuthService.login(identifier, password, requestedRole);

        if (session == null) {
            if ("Operator".equalsIgnoreCase(requestedRole)) {
                String msg = "Operator accounts must be pre-authorized by system administrators.";
                showInlineError(targetErrorLabel, msg);
                showAlert("Access Denied", msg);
            } else {
                String msg = "Invalid password or registration error.";
                showInlineError(targetErrorLabel, msg);
                showAlert("Login Failed", msg);
            }
            return;
        }

        if (!session.role.equalsIgnoreCase(requestedRole)) {
            String formattedRole = session.role.substring(0, 1).toUpperCase() + session.role.substring(1);
            String msg = "Unsuccessful Log In! Your account has '" + formattedRole + "' role. Please try again with correct Access Type.";
            showInlineError(targetErrorLabel, msg);
            showAlert("Access Denied", msg);
            return;
        }

        navigateToDashboard(event, session);
    }

    private void navigateToDashboard(ActionEvent event, AuthService.UserSession session) {
        try {
            FXMLLoader loader;
            Parent root;

            switch (session.role.toLowerCase()) {
                case "victim":
                    loader = new FXMLLoader(getClass().getResource("/com/pnrl/crisasterhub/fxml/victim.fxml"));
                    root = loader.load();

                    VictimController vc = loader.getController();
                    vc.setCurrentUserId(session.userId);
                    break;

                case "volunteer":
                    loader = new FXMLLoader(getClass().getResource("/com/pnrl/crisasterhub/fxml/volunteer.fxml"));
                    root = loader.load();

                    VolunteerController volCtrl = loader.getController();
                    volCtrl.setCurrentUserId(session.userId);
                    break;

                case "operator":
                    loader = new FXMLLoader(getClass().getResource("/com/pnrl/crisasterhub/fxml/operator.fxml"));
                    root = loader.load();
                    break;

                default:
                    showAlert("Error", "Unknown role.");
                    return;
            }

            Scene scene = new Scene(root);
            Stage stage = (Stage) ((Node) event.getSource()).getScene().getWindow();
            stage.setScene(scene);
            stage.setFullScreen(true);
            stage.centerOnScreen();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("System Error", "Could not load panel.");
        }
    }

    private void showInlineError(Label label, String message) {
        if (label != null) {
            label.setText(message);
            label.setVisible(true);
            label.setManaged(true);
        }
    }

    private void hideError(Label label) {
        if (label != null) {
            label.setVisible(false);
            label.setManaged(false);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
