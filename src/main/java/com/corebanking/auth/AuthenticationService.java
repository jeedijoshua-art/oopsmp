package com.corebanking.auth;

/**
 * Service contract for administrative user authentication in VaultLedger.
 */
public interface AuthenticationService {

    /**
     * Authenticates an administrative user.
     *
     * @param username user identifier
     * @param password raw input password
     * @return AuthResult containing success flag, display message, and failure categorization
     */
    AuthResult authenticate(String username, String password);

    /**
     * @return true if credentials have been properly configured
     */
    boolean isConfigured();

    /**
     * @return human-readable instructions on how to configure administrative credentials
     */
    String getConfigurationHelp();

    /**
     * @return the expected administrator username
     */
    String getExpectedUsername();
}

