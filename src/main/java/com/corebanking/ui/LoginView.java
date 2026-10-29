package com.corebanking.ui;

import com.corebanking.auth.AuthResult;
import com.corebanking.auth.AuthenticationService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.Separator;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * Premium banking-style Login View for VaultLedger.
 * Provides credentials input, show/hide password toggle, inline error messages,
 * and enters the main banking application on successful authentication.
 */
public final class LoginView {

    private final AuthenticationService authService;
    private final Consumer<String> onLoginSuccess;

    private final StackPane root = new StackPane();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();
    private final TextField passwordTextField = new TextField();
    private final ToggleButton passwordToggle = new ToggleButton("Show");
    private final Button signInButton = new Button("Sign In to VaultLedger");

    private final VBox errorBox = new VBox(4);
    private final Label errorTitleLabel = new Label();
    private final Label errorMessageLabel = new Label();

    private final VBox warningBox = new VBox(4);
    private final Label warningTitleLabel = new Label("Setup Required");
    private final Label warningMessageLabel = new Label();

    public LoginView(AuthenticationService authService, Consumer<String> onLoginSuccess) {
        this.authService = Objects.requireNonNull(authService, "authService must not be null");
        this.onLoginSuccess = Objects.requireNonNull(onLoginSuccess, "onLoginSuccess callback must not be null");
        buildUI();
    }

    private void buildUI() {
        root.getStyleClass().add("login-root");
        root.setAlignment(Pos.CENTER);

        VBox card = new VBox(20);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(460);
        card.setMinWidth(380);
        card.setAlignment(Pos.TOP_CENTER);

        // Header with Security Shield Icon and Branding
        SVGPath shieldIcon = new SVGPath();
        shieldIcon.setContent("M12 1L3 5v6c0 5.55 3.84 10.74 9 12 5.16-1.26 9-6.45 9-12V5l-9-4zm0 10.99h7c-.53 4.12-3.28 7.79-7 8.94V12H5V6.3l7-3.11v8.8z");
        shieldIcon.getStyleClass().add("login-shield-icon");

        StackPane iconBadge = new StackPane(shieldIcon);
        iconBadge.getStyleClass().add("login-icon-badge");

        Label brand = new Label("VAULTLEDGER");
        brand.getStyleClass().add("login-brand-title");

        Label subtitle = new Label("Core Banking Account Lifecycle & Ledger System");
        subtitle.getStyleClass().add("login-brand-subtitle");

        Label prompt = new Label("Authorized Personnel Access Only");
        prompt.getStyleClass().add("login-access-prompt");

        VBox headerBox = new VBox(8, iconBadge, brand, subtitle, prompt);
        headerBox.setAlignment(Pos.CENTER);

        // Setup warning banner (if credentials are not yet configured)
        warningBox.getStyleClass().add("login-warning-box");
        warningBox.setAlignment(Pos.CENTER_LEFT);
        warningTitleLabel.getStyleClass().add("login-warning-title");
        warningMessageLabel.getStyleClass().add("login-warning-text");
        warningMessageLabel.setWrapText(true);
        warningBox.getChildren().addAll(warningTitleLabel, warningMessageLabel);
        updateWarningState();

        // Error banner (hidden initially)
        errorBox.getStyleClass().add("login-error-box");
        errorBox.setAlignment(Pos.CENTER_LEFT);
        errorTitleLabel.getStyleClass().add("login-error-title");
        errorMessageLabel.getStyleClass().add("login-error-text");
        errorMessageLabel.setWrapText(true);
        errorBox.getChildren().addAll(errorTitleLabel, errorMessageLabel);
        errorBox.setVisible(false);
        errorBox.setManaged(false);

        // Form Fields
        VBox form = new VBox(14);
        form.setAlignment(Pos.CENTER_LEFT);

        // Username
        Label userLabel = new Label("Operator Username");
        userLabel.getStyleClass().add("login-field-label");
        usernameField.getStyleClass().addAll("text-field", "login-input-field");
        usernameField.setPromptText("Enter administrative username");
        usernameField.setText(authService.getExpectedUsername());
        usernameField.setOnAction(e -> handleSignIn());

        VBox userGroup = new VBox(6, userLabel, usernameField);

        // Password with Show/Hide toggle
        Label passLabel = new Label("Security Credential (Password)");
        passLabel.getStyleClass().add("login-field-label");

        passwordField.getStyleClass().addAll("password-field", "login-input-field");
        passwordField.setPromptText("Enter security password");
        passwordField.setOnAction(e -> handleSignIn());

        passwordTextField.getStyleClass().addAll("text-field", "login-input-field");
        passwordTextField.setPromptText("Enter security password");
        passwordTextField.setOnAction(e -> handleSignIn());

        // Bidirectional sync between masked and unmasked fields
        passwordTextField.textProperty().bindBidirectional(passwordField.textProperty());
        passwordTextField.setVisible(false);
        passwordTextField.setManaged(false);

        passwordToggle.getStyleClass().add("login-toggle-btn");
        passwordToggle.setFocusTraversable(false);
        passwordToggle.setOnAction(e -> {
            boolean show = passwordToggle.isSelected();
            passwordToggle.setText(show ? "Hide" : "Show");
            if (show) {
                passwordField.setVisible(false);
                passwordField.setManaged(false);
                passwordTextField.setVisible(true);
                passwordTextField.setManaged(true);
                passwordTextField.requestFocus();
                passwordTextField.positionCaret(passwordTextField.getText().length());
            } else {
                passwordTextField.setVisible(false);
                passwordTextField.setManaged(false);
                passwordField.setVisible(true);
                passwordField.setManaged(true);
                passwordField.requestFocus();
                passwordField.positionCaret(passwordField.getText().length());
            }
        });

        StackPane passwordInputs = new StackPane(passwordField, passwordTextField);
        HBox.setHgrow(passwordInputs, Priority.ALWAYS);

        HBox passwordBox = new HBox(8, passwordInputs, passwordToggle);
        passwordBox.setAlignment(Pos.CENTER_LEFT);

        VBox passGroup = new VBox(6, passLabel, passwordBox);

        // Sign In Button
        signInButton.getStyleClass().addAll("button", "primary-button", "login-submit-button");
        signInButton.setDefaultButton(true);
        signInButton.setMaxWidth(Double.MAX_VALUE);
        signInButton.setOnAction(e -> handleSignIn());

        // Helper / academic disclaimer
        Label demoHint = new Label("Academic Banking Simulation • Secure Session Control");
        demoHint.getStyleClass().add("login-hint-text");

        form.getChildren().addAll(userGroup, passGroup, signInButton, demoHint);

        card.getChildren().addAll(headerBox, warningBox, errorBox, form);

        // Footer note below card
        Label sysInfo = new Label("VaultLedger v1.0 • Academic Microproject Demonstration");
        sysInfo.getStyleClass().add("login-system-info");

        VBox layoutWrapper = new VBox(20, card, sysInfo);
        layoutWrapper.setAlignment(Pos.CENTER);
        layoutWrapper.setPadding(new Insets(24));

        root.getChildren().add(layoutWrapper);
    }

    private void updateWarningState() {
        if (!authService.isConfigured()) {
            warningBox.setVisible(true);
            warningBox.setManaged(true);
            warningMessageLabel.setText(
                    "Local administrative password has not been configured. " +
                    "Set VAULTLEDGER_ADMIN_PASSWORD in your environment or add it to data/auth.properties."
            );
        } else {
            warningBox.setVisible(false);
            warningBox.setManaged(false);
        }
    }

    public void handleSignIn() {
        hideError();
        String username = usernameField.getText();
        String password = passwordField.getText();

        signInButton.setDisable(true);
        signInButton.setText("Verifying Credentials...");

        try {
            AuthResult result = authService.authenticate(username, password);
            if (result.success()) {
                onLoginSuccess.accept(result.username());
            } else {
                showError("Authentication Failed", result.message());
                passwordField.selectAll();
                passwordTextField.selectAll();
                if (passwordField.isVisible()) {
                    passwordField.requestFocus();
                } else {
                    passwordTextField.requestFocus();
                }
            }
        } finally {
            signInButton.setDisable(false);
            signInButton.setText("Sign In to VaultLedger");
            updateWarningState();
        }
    }

    public void showError(String title, String message) {
        errorTitleLabel.setText(title);
        errorMessageLabel.setText(message);
        errorBox.setVisible(true);
        errorBox.setManaged(true);
    }

    public void hideError() {
        errorBox.setVisible(false);
        errorBox.setManaged(false);
    }

    public Parent getRoot() {
        return root;
    }

    // Getters for programmatic inspection and test harnesses
    public TextField getUsernameField() {
        return usernameField;
    }

    public PasswordField getPasswordField() {
        return passwordField;
    }

    public TextField getPasswordTextField() {
        return passwordTextField;
    }

    public ToggleButton getPasswordToggle() {
        return passwordToggle;
    }

    public Button getSignInButton() {
        return signInButton;
    }

    public VBox getErrorBox() {
        return errorBox;
    }

    public Label getErrorMessageLabel() {
        return errorMessageLabel;
    }

    public VBox getWarningBox() {
        return warningBox;
    }
}

