package com.corebanking;

import com.corebanking.auth.AuthenticationService;
import com.corebanking.auth.AuthenticationServiceImpl;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.config.DatabaseConnection;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.service.AccountService;
import com.corebanking.service.AccountServiceImpl;
import com.corebanking.service.BankService;
import com.corebanking.service.BankServiceImpl;
import com.corebanking.service.TransactionService;
import com.corebanking.service.TransactionServiceImpl;
import com.corebanking.service.DatabaseManagementService;
import com.corebanking.service.DatabaseManagementServiceImpl;
import com.corebanking.ui.AppShell;
import com.corebanking.ui.LoginView;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.Connection;

public final class Main extends Application {
    private Connection connection;
    private AccountService accountService;
    private BankService bankService;
    private TransactionService transactionService;
    private DatabaseManagementService dbmService;
    private AuthenticationService authService;

    @Override
    public void start(Stage stage) throws Exception {
        connection = DatabaseConnection.openConnection(com.corebanking.config.AppConfig.databaseFile());
        AccountRepository accountRepository = new AccountRepository(connection);
        accountService = new AccountServiceImpl(accountRepository, new BankRepository(connection));
        bankService = new BankServiceImpl(new BankRepository(connection));
        transactionService = new TransactionServiceImpl(connection, accountService);
        dbmService = new DatabaseManagementServiceImpl(connection);
        authService = new AuthenticationServiceImpl();

        LoginView loginView = new LoginView(authService, username -> showDashboard(stage, username));
        Scene scene = new Scene(loginView.getRoot(), 1240, 780);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

        stage.setTitle("VaultLedger — Operator Sign In");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> {
            closeConnection();
            Platform.exit();
            System.exit(0);
        });
        stage.show();
    }

    private void showDashboard(Stage stage, String authenticatedUser) {
        AppShell shell = new AppShell(
                connection,
                accountService,
                bankService,
                transactionService,
                dbmService,
                authenticatedUser,
                () -> showLogin(stage)
        );
        stage.getScene().setRoot(shell.build());
        stage.setTitle("VaultLedger — Core Banking Account Lifecycle & Ledger System [" + authenticatedUser + "]");
    }

    private void showLogin(Stage stage) {
        LoginView loginView = new LoginView(authService, username -> showDashboard(stage, username));
        stage.getScene().setRoot(loginView.getRoot());
        stage.setTitle("VaultLedger — Operator Sign In");
    }

    @Override
    public void stop() {
        closeConnection();
    }

    private void closeConnection() {
        if (connection != null) {
            try {
                connection.close();
            } catch (Exception ignored) {
                // The application is already shutting down.
            }
            connection = null;
        }
    }

    public static void main(String[] args) {
        try {
            DatabaseInitializer.initialize();
            launch(args);
        } catch (Exception exception) {
            System.err.println("Application startup failed: " + exception.getMessage());
            System.exit(1);
        }
    }
}
