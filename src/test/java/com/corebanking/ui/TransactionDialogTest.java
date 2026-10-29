package com.corebanking.ui;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.config.DemoDataSeeder;
import com.corebanking.enums.TransactionType;
import com.corebanking.model.Account;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.service.*;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class TransactionDialogTest {
    private static boolean javafxAvailable = true;

    @BeforeAll
    public static void initJavaFx() {
        String os = System.getProperty("os.name", "").toLowerCase();
        if (os.contains("linux") && System.getenv("DISPLAY") == null) {
            javafxAvailable = false;
            return;
        }
        try {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.startup(latch::countDown);
            if (!latch.await(5, TimeUnit.SECONDS)) {
                javafxAvailable = false;
            }
        } catch (IllegalStateException e) {
            // Already started
        } catch (Throwable t) {
            javafxAvailable = false;
        }
    }

    @Test
    public void testTransactionOperationsIntegrity() throws Exception {
        Path tempDb = Files.createTempFile("test-txn-dialog", ".db");
        DatabaseInitializer.initialize(tempDb);

        try (Connection connection = DatabaseConnection.openConnection(tempDb)) {
            AccountRepository accountRepo = new AccountRepository(connection);
            BankRepository bankRepo = new BankRepository(connection);
            AccountService accountService = new AccountServiceImpl(accountRepo, bankRepo);
            BankService bankService = new BankServiceImpl(bankRepo);
            TransactionService transactionService = new TransactionServiceImpl(connection, accountService);

            DemoDataSeeder.seed(connection, bankService, accountService, transactionService);

            Account joshua = accountService.findAccount("ACC-JOSHUA");
            BigDecimal initialJoshua = joshua.getBalance();

            // Test Deposit
            transactionService.deposit("ACC-JOSHUA", new BigDecimal("100.00"), "Test Deposit");
            assertEquals(initialJoshua.add(new BigDecimal("100.00")), accountService.findAccount("ACC-JOSHUA").getBalance());

            // Test Withdrawal
            transactionService.withdraw("ACC-JOSHUA", new BigDecimal("50.00"), "Test Withdrawal");
            assertEquals(initialJoshua.add(new BigDecimal("50.00")), accountService.findAccount("ACC-JOSHUA").getBalance());

            // Test Transfer
            Account alice = accountService.findAccount("ACC-ALICE");
            BigDecimal initialAlice = alice.getBalance();
            transactionService.transfer("ACC-JOSHUA", "ACC-ALICE", new BigDecimal("200.00"), "Test Transfer");
            assertEquals(initialJoshua.subtract(new BigDecimal("150.00")), accountService.findAccount("ACC-JOSHUA").getBalance());
            assertEquals(initialAlice.add(new BigDecimal("200.00")), accountService.findAccount("ACC-ALICE").getBalance());

            // Test Insufficient Funds Exception
            assertThrows(com.corebanking.exception.InsufficientFundsException.class, () ->
                    transactionService.withdraw("ACC-ALICE", new BigDecimal("9999999.00"), "Exceeds balance"));

            // Test Same Account Transfer Exception
            assertThrows(com.corebanking.exception.InvalidTransferException.class, () ->
                    transactionService.transfer("ACC-ALICE", "ACC-ALICE", new BigDecimal("10.00"), "Self transfer"));

            // Test Account equals/hashCode in Collections
            Account freshAlice = accountService.findAccount("ACC-ALICE");
            assertEquals(alice, freshAlice);
            assertTrue(accountService.listAccounts().contains(freshAlice));
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }

    @Test
    public void testDialogLayoutNodesInPlatform() throws Exception {
        if (!javafxAvailable) {
            return;
        }
        Path tempDb = Files.createTempFile("test-txn-ui", ".db");
        DatabaseInitializer.initialize(tempDb);

        try (Connection connection = DatabaseConnection.openConnection(tempDb)) {
            AccountRepository accountRepo = new AccountRepository(connection);
            BankRepository bankRepo = new BankRepository(connection);
            AccountService accountService = new AccountServiceImpl(accountRepo, bankRepo);
            BankService bankService = new BankServiceImpl(bankRepo);
            TransactionService transactionService = new TransactionServiceImpl(connection, accountService);
            DatabaseManagementService dbmService = new DatabaseManagementServiceImpl(connection);

            DemoDataSeeder.seed(connection, bankService, accountService, transactionService);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            Platform.runLater(() -> {
                try {
                    AppShell shell = new AppShell(connection, accountService, bankService, transactionService, dbmService);
                    Parent root = shell.build();
                    assertNotNull(root);
                } catch (Throwable t) {
                    errorRef.set(t);
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS));
            if (errorRef.get() != null) {
                fail(errorRef.get());
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}

