package com.corebanking.ui;

import com.corebanking.auth.AuthCredentialsProvider;
import com.corebanking.auth.AuthenticationService;
import com.corebanking.auth.AuthenticationServiceImpl;
import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.service.*;
import javafx.application.Platform;
import javafx.scene.control.Button;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

public class LoginFlowUITest {
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

    private static final class TestCredentialsProvider implements AuthCredentialsProvider {
        private final String user;
        private final String pass;

        TestCredentialsProvider(String user, String pass) {
            this.user = user;
            this.pass = pass;
        }

        @Override
        public String getAdminUsername() {
            return user;
        }

        @Override
        public char[] getAdminPassword() {
            return pass.toCharArray();
        }

        @Override
        public boolean isConfigured() {
            return pass != null && !pass.isEmpty();
        }

        @Override
        public String getSourceDescription() {
            return "Test in-memory credentials";
        }
    }

    @Test
    public void testLoginViewUIElementsAndPasswordToggle() throws Exception {
        if (!javafxAvailable) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                AuthenticationService authService = new AuthenticationServiceImpl(
                        new TestCredentialsProvider("admin", "admin@123")
                );

                AtomicBoolean loggedIn = new AtomicBoolean(false);
                LoginView loginView = new LoginView(authService, user -> loggedIn.set(true));

                assertNotNull(loginView.getRoot());
                assertEquals("admin", loginView.getUsernameField().getText());
                assertTrue(loginView.getPasswordField().isVisible());
                assertFalse(loginView.getPasswordTextField().isVisible());
                assertEquals("Show", loginView.getPasswordToggle().getText());

                // Toggle to show password (fire toggles selected state and triggers onAction)
                loginView.getPasswordToggle().fire();
                assertTrue(loginView.getPasswordTextField().isVisible());
                assertFalse(loginView.getPasswordField().isVisible());
                assertEquals("Hide", loginView.getPasswordToggle().getText());

                // Toggle back to hide password
                loginView.getPasswordToggle().fire();
                assertTrue(loginView.getPasswordField().isVisible());
                assertFalse(loginView.getPasswordTextField().isVisible());
                assertEquals("Show", loginView.getPasswordToggle().getText());

            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX thread timed out");
        if (errorRef.get() != null) {
            fail("Exception on JavaFX thread: " + errorRef.get().getMessage(), errorRef.get());
        }
    }

    @Test
    public void testLoginFailureDisplaysInlineError() throws Exception {
        if (!javafxAvailable) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                AuthenticationService authService = new AuthenticationServiceImpl(
                        new TestCredentialsProvider("admin", "admin@123")
                );

                AtomicBoolean loggedIn = new AtomicBoolean(false);
                LoginView loginView = new LoginView(authService, user -> loggedIn.set(true));

                // Enter wrong password
                loginView.getUsernameField().setText("admin");
                loginView.getPasswordField().setText("wrongPass");

                loginView.handleSignIn();

                assertFalse(loggedIn.get(), "User should not be logged in with wrong password");
                assertTrue(loginView.getErrorBox().isVisible());
                assertTrue(loginView.getErrorMessageLabel().getText().contains("Invalid username or password"));

            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX thread timed out");
        if (errorRef.get() != null) {
            fail("Exception on JavaFX thread: " + errorRef.get().getMessage(), errorRef.get());
        }
    }

    @Test
    public void testLoginSuccessTriggersCallback() throws Exception {
        if (!javafxAvailable) {
            return;
        }

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> errorRef = new AtomicReference<>();

        Platform.runLater(() -> {
            try {
                AuthenticationService authService = new AuthenticationServiceImpl(
                        new TestCredentialsProvider("admin", "admin@123")
                );

                AtomicReference<String> loggedInUser = new AtomicReference<>();
                LoginView loginView = new LoginView(authService, loggedInUser::set);

                loginView.getUsernameField().setText("admin");
                loginView.getPasswordField().setText("admin@123");

                loginView.handleSignIn();

                assertEquals("admin", loggedInUser.get());
                assertFalse(loginView.getErrorBox().isVisible());

            } catch (Throwable t) {
                errorRef.set(t);
            } finally {
                latch.countDown();
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX thread timed out");
        if (errorRef.get() != null) {
            fail("Exception on JavaFX thread: " + errorRef.get().getMessage(), errorRef.get());
        }
    }

    @Test
    public void testAppShellLogoutCallback() throws Exception {
        if (!javafxAvailable) {
            return;
        }

        Path tempDb = Files.createTempFile("test-logout", ".db");
        DatabaseInitializer.initialize(tempDb);

        try (Connection connection = DatabaseConnection.openConnection(tempDb)) {
            AccountRepository accountRepo = new AccountRepository(connection);
            BankRepository bankRepo = new BankRepository(connection);
            AccountService accountService = new AccountServiceImpl(accountRepo, bankRepo);
            BankService bankService = new BankServiceImpl(bankRepo);
            TransactionService transactionService = new TransactionServiceImpl(connection, accountService);
            DatabaseManagementService dbmService = new DatabaseManagementServiceImpl(connection);

            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();
            AtomicBoolean loggedOut = new AtomicBoolean(false);

            Platform.runLater(() -> {
                try {
                    AppShell shell = new AppShell(
                            connection,
                            accountService,
                            bankService,
                            transactionService,
                            dbmService,
                            "admin",
                            () -> loggedOut.set(true)
                    );

                    javafx.scene.Parent root = shell.build();
                    assertNotNull(root);

                    // Find sign out button in the built tree
                    Button logoutButton = (Button) root.lookup(".logout-button");
                    assertNotNull(logoutButton, "Header logout button should exist");
                    logoutButton.fire();

                    assertTrue(loggedOut.get(), "Sign Out button should trigger onLogout callback");

                } catch (Throwable t) {
                    errorRef.set(t);
                } finally {
                    latch.countDown();
                }
            });

            assertTrue(latch.await(5, TimeUnit.SECONDS), "JavaFX thread timed out");
            if (errorRef.get() != null) {
                fail("Exception on JavaFX thread: " + errorRef.get().getMessage(), errorRef.get());
            }
        } finally {
            Files.deleteIfExists(tempDb);
        }
    }
}
