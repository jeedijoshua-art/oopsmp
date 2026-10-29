package com.corebanking.auth;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class AuthenticationServiceTest {

    private Path tempConfigFile;

    @BeforeEach
    public void setUp() throws IOException {
        System.clearProperty("vaultledger.admin.username");
        System.clearProperty("vaultledger.admin.password");
    }

    @AfterEach
    public void tearDown() throws IOException {
        System.clearProperty("vaultledger.admin.username");
        System.clearProperty("vaultledger.admin.password");
        if (tempConfigFile != null && Files.exists(tempConfigFile)) {
            Files.deleteIfExists(tempConfigFile);
        }
    }

    @Test
    public void testAuthenticationSuccessWithSystemProperties() {
        System.setProperty("vaultledger.admin.username", "admin");
        System.setProperty("vaultledger.admin.password", "secretPass123");

        LocalFileOrEnvCredentialsProvider provider = new LocalFileOrEnvCredentialsProvider(Path.of("non-existent-auth.properties"));
        AuthenticationService service = new AuthenticationServiceImpl(provider);

        assertTrue(service.isConfigured());
        assertEquals("admin", service.getExpectedUsername());

        AuthResult result = service.authenticate("admin", "secretPass123");
        assertTrue(result.success());
        assertEquals("admin", result.username());
        assertNull(result.failureReason());
    }

    @Test
    public void testAuthenticationFailureWrongPassword() {
        System.setProperty("vaultledger.admin.username", "admin");
        System.setProperty("vaultledger.admin.password", "correctPassword");

        LocalFileOrEnvCredentialsProvider provider = new LocalFileOrEnvCredentialsProvider(Path.of("non-existent-auth.properties"));
        AuthenticationService service = new AuthenticationServiceImpl(provider);

        AuthResult result = service.authenticate("admin", "wrongPassword");
        assertFalse(result.success());
        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, result.failureReason());
        assertEquals("Invalid username or password.", result.message());
        assertNull(result.username());
    }

    @Test
    public void testAuthenticationFailureWrongUsername() {
        System.setProperty("vaultledger.admin.username", "admin");
        System.setProperty("vaultledger.admin.password", "correctPassword");

        LocalFileOrEnvCredentialsProvider provider = new LocalFileOrEnvCredentialsProvider(Path.of("non-existent-auth.properties"));
        AuthenticationService service = new AuthenticationServiceImpl(provider);

        AuthResult result = service.authenticate("operator2", "correctPassword");
        assertFalse(result.success());
        assertEquals(AuthFailureReason.INVALID_CREDENTIALS, result.failureReason());
        assertEquals("Invalid username or password.", result.message());
    }

    @Test
    public void testAuthenticationEmptyInputs() {
        System.setProperty("vaultledger.admin.password", "testPass");

        LocalFileOrEnvCredentialsProvider provider = new LocalFileOrEnvCredentialsProvider(Path.of("non-existent-auth.properties"));
        AuthenticationService service = new AuthenticationServiceImpl(provider);

        AuthResult r1 = service.authenticate("", "testPass");
        assertFalse(r1.success());
        assertEquals(AuthFailureReason.EMPTY_INPUT, r1.failureReason());

        AuthResult r2 = service.authenticate("   ", "testPass");
        assertFalse(r2.success());
        assertEquals(AuthFailureReason.EMPTY_INPUT, r2.failureReason());

        AuthResult r3 = service.authenticate("admin", "");
        assertFalse(r3.success());
        assertEquals(AuthFailureReason.EMPTY_INPUT, r3.failureReason());

        AuthResult r4 = service.authenticate(null, "testPass");
        assertFalse(r4.success());
        assertEquals(AuthFailureReason.EMPTY_INPUT, r4.failureReason());

        AuthResult r5 = service.authenticate("admin", null);
        assertFalse(r5.success());
        assertEquals(AuthFailureReason.EMPTY_INPUT, r5.failureReason());
    }

    @Test
    public void testAuthenticationFailsClosedWhenUnconfigured() {
        // Point to empty or non-existent file with no sysprops
        LocalFileOrEnvCredentialsProvider provider = new LocalFileOrEnvCredentialsProvider(Path.of("target", "does-not-exist.properties"));
        AuthenticationService service = new AuthenticationServiceImpl(provider);

        assertFalse(service.isConfigured());
        AuthResult result = service.authenticate("admin", "admin@123");
        assertFalse(result.success());
        assertEquals(AuthFailureReason.NOT_CONFIGURED, result.failureReason());
        assertTrue(result.message().contains("not configured"));
    }

    @Test
    public void testAuthenticationWithPropertiesFile() throws IOException {
        tempConfigFile = Files.createTempFile("auth-test-", ".properties");
        Files.writeString(tempConfigFile, "vaultledger.admin.username=auditor\nvaultledger.admin.password=Audit#2026\n");

        LocalFileOrEnvCredentialsProvider provider = new LocalFileOrEnvCredentialsProvider(tempConfigFile);
        AuthenticationService service = new AuthenticationServiceImpl(provider);

        assertTrue(service.isConfigured());
        assertEquals("auditor", service.getExpectedUsername());

        AuthResult result = service.authenticate("auditor", "Audit#2026");
        assertTrue(result.success());
        assertEquals("auditor", result.username());
    }
}

