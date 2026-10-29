package com.corebanking.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

/**
 * Standard implementation of AuthenticationService using constant-time byte comparisons
 * and failing closed when credentials are not configured.
 */
public final class AuthenticationServiceImpl implements AuthenticationService {

    private final AuthCredentialsProvider credentialsProvider;

    public AuthenticationServiceImpl() {
        this(new LocalFileOrEnvCredentialsProvider());
    }

    public AuthenticationServiceImpl(AuthCredentialsProvider credentialsProvider) {
        this.credentialsProvider = Objects.requireNonNull(credentialsProvider, "credentialsProvider must not be null");
    }

    @Override
    public AuthResult authenticate(String username, String password) {
        if (username == null || username.trim().isEmpty() || password == null || password.isEmpty()) {
            return AuthResult.failure(AuthFailureReason.EMPTY_INPUT, "Please enter both username and password.");
        }

        if (!credentialsProvider.isConfigured()) {
            return AuthResult.failure(
                    AuthFailureReason.NOT_CONFIGURED,
                    "Authentication is not configured. Please set the VAULTLEDGER_ADMIN_PASSWORD environment variable, " +
                    "use -Dvaultledger.admin.password, or provide data/auth.properties."
            );
        }

        String expectedUser = credentialsProvider.getAdminUsername();
        char[] expectedPassChars = credentialsProvider.getAdminPassword();

        byte[] inputUserBytes = username.trim().getBytes(StandardCharsets.UTF_8);
        byte[] expectedUserBytes = expectedUser.getBytes(StandardCharsets.UTF_8);

        byte[] inputPassBytes = password.getBytes(StandardCharsets.UTF_8);
        byte[] expectedPassBytes = new String(expectedPassChars).getBytes(StandardCharsets.UTF_8);

        // Constant-time comparisons using MessageDigest.isEqual to mitigate timing side-channel attacks
        boolean usernameMatches = MessageDigest.isEqual(inputUserBytes, expectedUserBytes);
        boolean passwordMatches = MessageDigest.isEqual(inputPassBytes, expectedPassBytes);

        if (usernameMatches && passwordMatches) {
            return AuthResult.success(expectedUser, "Authentication successful. Welcome, " + expectedUser + ".");
        }

        // Generic error message to prevent account enumeration / reconnaissance
        return AuthResult.failure(AuthFailureReason.INVALID_CREDENTIALS, "Invalid username or password.");
    }

    @Override
    public boolean isConfigured() {
        return credentialsProvider.isConfigured();
    }

    @Override
    public String getConfigurationHelp() {
        return credentialsProvider.getSourceDescription();
    }

    @Override
    public String getExpectedUsername() {
        return credentialsProvider.getAdminUsername();
    }
}

