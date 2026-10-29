package com.corebanking.ui;

import com.corebanking.config.DatabaseInitializer;
import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DemoDataSeeder;
import com.corebanking.enums.TransactionType;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.service.*;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.control.Hyperlink;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class AppShellUITest {
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
    public void testAppShellBuildAndNavigation() throws Exception {
        if (!javafxAvailable) {
            return;
        }
        Path tempDb = Files.createTempFile("test-ui-vaultledger", ".db");
        DatabaseInitializer.initialize(tempDb);

        try (Connection connection = DatabaseConnection.openConnection(tempDb)) {
            AccountRepository accountRepo = new AccountRepository(connection);
            BankRepository bankRepo = new BankRepository(connection);
            AccountService accountService = new AccountServiceImpl(accountRepo, bankRepo);
            BankService bankService = new BankServiceImpl(bankRepo);
            TransactionService transactionService = new TransactionServiceImpl(connection, accountService);
            DatabaseManagementService dbmService = new DatabaseManagementServiceImpl(connection);

            // Seed demo data to test tables with content
            DemoDataSeeder.seed(connection, bankService, accountService, transactionService);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            Platform.runLater(() -> {
                try {
                    AppShell shell = new AppShell(connection, accountService, bankService, transactionService, dbmService);
                    Parent root = shell.build();
                    assertNotNull(root);
                    assertTrue(root.getStyleClass().contains("app-root"));
                    Hyperlink link = (Hyperlink) root.lookup(".github-link");
                    assertNotNull(link, "GitHub link should be present in sidebar");
                    assertNotNull(link.getGraphic(), "GitHub graphic should be present on link");
                } catch (Throwable t) {
                    errorRef.set(t);
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS), "UI did not render in time");
            if (errorRef.get() != null) {
                fail("UI error: " + errorRef.get().getMessage(), errorRef.get());
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}
