package com.corebanking.config;

import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseConfig {
    private DatabaseConfig() {
    }

    public static Connection openConnection() throws SQLException {
        return DatabaseConnection.openConnection(AppConfig.databaseFile());
    }
}
