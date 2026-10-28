package com.corebanking;

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
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.sql.Connection;

public final class Main extends Application {
    private Connection connection;

    @Override
    public void start(Stage stage) throws Exception {
        connection = DatabaseConnection.openConnection(com.corebanking.config.AppConfig.databaseFile());
        AccountRepository accountRepository = new AccountRepository(connection);
        AccountService accountService = new AccountServiceImpl(accountRepository, new BankRepository(connection));
        BankService bankService = new BankServiceImpl(new BankRepository(connection));
        TransactionService transactionService = new TransactionServiceImpl(connection, accountService);

        com.corebanking.service.DatabaseManagementService dbmService = new com.corebanking.service.DatabaseManagementServiceImpl(connection);
        AppShell shell = new AppShell(connection, accountService, bankService, transactionService, dbmService);

        Scene scene = new Scene(shell.build(), 1340, 840);
        scene.getStylesheets().add(getClass().getResource("/css/application.css").toExternalForm());

        stage.setTitle("VaultLedger — Core Banking Account Lifecycle & Ledger System");
        stage.setMinWidth(1100);
        stage.setMinHeight(720);
        stage.setScene(scene);
        stage.setOnCloseRequest(event -> closeConnection());
        stage.show();
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
