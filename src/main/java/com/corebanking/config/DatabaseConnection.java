package com.corebanking.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {
    private DatabaseConnection() {
    }

    public static Connection openConnection(Path databaseFile) throws SQLException {
        try {
            Files.createDirectories(databaseFile.toAbsolutePath().getParent());
        } catch (IOException exception) {
            throw new SQLException("Unable to create the database directory.", exception);
        }

        Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databaseFile);
        try (var statement = connection.createStatement()) {
            statement.execute("PRAGMA foreign_keys = ON");
            statement.execute("PRAGMA busy_timeout = 5000");
        }
        return connection;
    }
}