package com.corebanking.auth;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Resolves administrative credentials safely using a hierarchical strategy:
 * 1. JVM system properties (-Dvaultledger.admin.password=...)
 * 2. Operating system environment variables (VAULTLEDGER_ADMIN_PASSWORD=...)
 * 3. Git-ignored local configuration file (data/auth.properties)
 *
 * Never hardcodes or logs passwords. Fails closed if no configuration is found.
 */
public final class LocalFileOrEnvCredentialsProvider implements AuthCredentialsProvider {

    public static final String DEFAULT_ADMIN_USERNAME = "admin";
    public static final Path DEFAULT_AUTH_PROPERTIES_PATH = Path.of("data", "auth.properties");

    private final Path customPropertiesPath;
    private String resolvedUsername;
    private char[] resolvedPassword;
    private boolean configured;
    private String sourceDescription;

    public LocalFileOrEnvCredentialsProvider() {
        this(DEFAULT_AUTH_PROPERTIES_PATH);
    }

    public LocalFileOrEnvCredentialsProvider(Path customPropertiesPath) {
        this.customPropertiesPath = customPropertiesPath;
        reload();
    }

    /**
     * Re-evaluates configuration sources in priority order.
     */
    public synchronized void reload() {
        // 1. Check System Properties
        String sysPropPassword = System.getProperty("vaultledger.admin.password");
        if (sysPropPassword != null && !sysPropPassword.trim().isEmpty()) {
            String sysPropUser = System.getProperty("vaultledger.admin.username", DEFAULT_ADMIN_USERNAME);
            this.resolvedUsername = sysPropUser.trim().isEmpty() ? DEFAULT_ADMIN_USERNAME : sysPropUser.trim();
            this.resolvedPassword = sysPropPassword.toCharArray();
            this.configured = true;
            this.sourceDescription = "JVM system property (vaultledger.admin.password)";
            return;
        }

        // 2. Check Environment Variables
        String envPassword = System.getenv("VAULTLEDGER_ADMIN_PASSWORD");
        if (envPassword != null && !envPassword.trim().isEmpty()) {
            String envUser = System.getenv("VAULTLEDGER_ADMIN_USERNAME");
            this.resolvedUsername = (envUser != null && !envUser.trim().isEmpty()) ? envUser.trim() : DEFAULT_ADMIN_USERNAME;
            this.resolvedPassword = envPassword.toCharArray();
            this.configured = true;
            this.sourceDescription = "Environment variable (VAULTLEDGER_ADMIN_PASSWORD)";
            return;
        }

        // 3. Check Git-ignored local file
        Path propertiesPath = (customPropertiesPath != null) ? customPropertiesPath : DEFAULT_AUTH_PROPERTIES_PATH;
        if (Files.isRegularFile(propertiesPath)) {
            try (InputStream in = Files.newInputStream(propertiesPath)) {
                Properties props = new Properties();
                props.load(in);

                String filePassword = props.getProperty("vaultledger.admin.password");
                if (filePassword == null || filePassword.trim().isEmpty()) {
                    filePassword = props.getProperty("admin.password");
                }

                if (filePassword != null && !filePassword.trim().isEmpty()) {
                    String fileUser = props.getProperty("vaultledger.admin.username");
                    if (fileUser == null || fileUser.trim().isEmpty()) {
                        fileUser = props.getProperty("admin.username", DEFAULT_ADMIN_USERNAME);
                    }
                    this.resolvedUsername = (fileUser != null && !fileUser.trim().isEmpty()) ? fileUser.trim() : DEFAULT_ADMIN_USERNAME;
                    this.resolvedPassword = filePassword.toCharArray();
                    this.configured = true;
                    this.sourceDescription = "Local configuration file (" + propertiesPath.toString() + ")";
                    return;
                }
            } catch (Exception ignored) {
                // Ignore file read error and fall through to unconfigured
            }
        }

        // 4. Unconfigured
        this.resolvedUsername = DEFAULT_ADMIN_USERNAME;
        this.resolvedPassword = new char[0];
        this.configured = false;
        this.sourceDescription = "Not configured. Set VAULTLEDGER_ADMIN_PASSWORD or define vaultledger.admin.password in data/auth.properties";
    }

    @Override
    public synchronized String getAdminUsername() {
        return resolvedUsername != null ? resolvedUsername : DEFAULT_ADMIN_USERNAME;
    }

    @Override
    public synchronized char[] getAdminPassword() {
        return resolvedPassword != null ? resolvedPassword.clone() : new char[0];
    }

    @Override
    public synchronized boolean isConfigured() {
        return configured;
    }

    @Override
    public synchronized String getSourceDescription() {
        return sourceDescription;
    }
}

