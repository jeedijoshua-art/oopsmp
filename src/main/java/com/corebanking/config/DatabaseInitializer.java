package com.corebanking.config;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;

public final class DatabaseInitializer {
    private DatabaseInitializer() {
    }

    public static void initialize() throws IOException, SQLException {
        initialize(AppConfig.databaseFile());
    }

    public static void initialize(java.nio.file.Path databaseFile) throws IOException, SQLException {
        Files.createDirectories(databaseFile.toAbsolutePath().getParent());

        try (InputStream schemaStream = DatabaseInitializer.class.getResourceAsStream("/database/schema.sql")) {
            if (schemaStream == null) {
                throw new IOException("Database schema resource was not found.");
            }

            String schema = new String(schemaStream.readAllBytes(), StandardCharsets.UTF_8);
              try (Connection connection = DatabaseConnection.openConnection(databaseFile);
                 var statement = connection.createStatement()) {
                for (String sqlStatement : schema.split(";")) {
                    String trimmedStatement = sqlStatement.trim();
                    if (!trimmedStatement.isEmpty()) {
                        statement.execute(trimmedStatement);
                    }
                }
                addLedgerDescriptionColumnIfMissing(connection);
            }
        }
    }

    private static void addLedgerDescriptionColumnIfMissing(Connection connection) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(null, null, "ledger_entries", "description")) {
            if (!columns.next()) {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE ledger_entries ADD COLUMN description TEXT");
                }
            }
        }
    }
}
