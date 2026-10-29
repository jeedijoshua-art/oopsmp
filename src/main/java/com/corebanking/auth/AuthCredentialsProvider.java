package com.corebanking.auth;

/**
 * Provider interface for resolving administrative credentials from local configuration or environment.
 */
public interface AuthCredentialsProvider {

    /**
     * @return the expected administrator username (default: "admin")
     */
    String getAdminUsername();

    /**
     * @return the expected administrator password as a character array
     */
    char[] getAdminPassword();

    /**
     * @return true if an administrative password has been configured
     */
    boolean isConfigured();

    /**
     * @return a human-readable description of where credentials were found or instructions if missing
     */
    String getSourceDescription();
}

