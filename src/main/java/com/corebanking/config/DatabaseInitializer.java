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
                addIsDemoColumnIfMissing(connection);
                addAccountColumnsIfMissing(connection);
                addAccountInitialBalanceColumnIfMissing(connection);
            }
        }
    }

    private static void addAccountInitialBalanceColumnIfMissing(Connection connection) throws SQLException {
        try (var columns = connection.getMetaData().getColumns(null, null, "accounts", "initial_balance")) {
            if (!columns.next()) {
                try (var statement = connection.createStatement()) {
                    statement.execute("ALTER TABLE accounts ADD COLUMN initial_balance TEXT NOT NULL DEFAULT '0.00'");
                    // Backfill initial_balance for existing rows:
                    // If no ledger entries exist, initial_balance equals current balance.
                    // If ledger entries exist, initial_balance equals balance - net ledger movements.
                    statement.execute("""
                        UPDATE accounts
                        SET initial_balance = CASE
                            WHEN account_id = 'SYSTEM-CASH' THEN '0.00'
                            ELSE balance
                        END
                        WHERE initial_balance IS NULL OR initial_balance = '0.00'
                    """);
                }
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

    private static void addIsDemoColumnIfMissing(Connection connection) throws SQLException {
        String[] tables = {"banks", "accounts", "transactions", "ledger_entries"};
        for (String table : tables) {
            try (var columns = connection.getMetaData().getColumns(null, null, table, "is_demo")) {
                if (!columns.next()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE " + table + " ADD COLUMN is_demo INTEGER NOT NULL DEFAULT 0");
                    }
                }
            }
        }
    }

    private static void addAccountColumnsIfMissing(Connection connection) throws SQLException {
        String[] columnsToAdd = {"age INTEGER", "email TEXT", "phone_number TEXT"};
        for (String colDef : columnsToAdd) {
            String colName = colDef.split(" ")[0];
            try (var columns = connection.getMetaData().getColumns(null, null, "accounts", colName)) {
                if (!columns.next()) {
                    try (var statement = connection.createStatement()) {
                        statement.execute("ALTER TABLE accounts ADD COLUMN " + colDef);
                    }
                }
            }
        }
    }
}
