package com.corebanking.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DatabaseLayerTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void connectionEnablesForeignKeys() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("connection.db");
        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var statement = connection.createStatement();
             var resultSet = statement.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    void initializerCreatesRequiredTablesAndColumns() throws Exception {
        Path databaseFile = initializedDatabase("schema.db");
        try (var connection = DatabaseConnection.openConnection(databaseFile)) {
            Set<String> tables = new HashSet<>();
            try (var resultSet = connection.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
                while (resultSet.next()) {
                    tables.add(resultSet.getString("TABLE_NAME"));
                }
            }

            assertTrue(tables.containsAll(Set.of("banks", "accounts", "transactions", "ledger_entries")));

            try (var resultSet = connection.getMetaData().getColumns(null, null, "ledger_entries", "description")) {
                assertTrue(resultSet.next());
            }
        }
    }

    @Test
    void initializerMigratesExistingLedgerTable() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("legacy.db");
        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var statement = connection.createStatement()) {
            statement.execute("CREATE TABLE ledger_entries ("
                    + "ledger_entry_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                    + "transaction_id TEXT NOT NULL, "
                    + "account_id TEXT NOT NULL, "
                    + "entry_type TEXT NOT NULL, "
                    + "amount TEXT NOT NULL, "
                    + "created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        }

        DatabaseInitializer.initialize(databaseFile);

        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var resultSet = connection.getMetaData().getColumns(null, null, "ledger_entries", "description")) {
            assertTrue(resultSet.next());
        }
    }

    @Test
    void foreignKeyConstraintRejectsUnknownBank() throws Exception {
        Path databaseFile = initializedDatabase("foreign-key.db");
        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var statement = connection.prepareStatement(
                     "INSERT INTO accounts (account_id, customer_name, bank_id, account_type) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, "A001");
            statement.setString(2, "Test Customer");
            statement.setInt(3, 999);
            statement.setString(4, "SAVINGS");
            assertThrows(SQLException.class, statement::executeUpdate);
        }
    }

    @Test
    void transactionCommitPersistsChanges() throws Exception {
        Path databaseFile = initializedDatabase("commit.db");
        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var transaction = new DatabaseTransaction(connection);
             var statement = connection.prepareStatement(
                     "INSERT INTO banks (bank_code, bank_name) VALUES (?, ?)")) {
            statement.setString(1, "BANK-C");
            statement.setString(2, "Committed Bank");
            statement.executeUpdate();
            transaction.commit();
        }

        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) FROM banks")) {
            assertTrue(resultSet.next());
            assertEquals(1, resultSet.getInt(1));
        }
    }

    @Test
    void transactionRollbackDiscardsChanges() throws Exception {
        Path databaseFile = initializedDatabase("rollback.db");
        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var transaction = new DatabaseTransaction(connection);
             var statement = connection.prepareStatement(
                     "INSERT INTO banks (bank_code, bank_name) VALUES (?, ?)")) {
            statement.setString(1, "BANK-R");
            statement.setString(2, "Rolled Back Bank");
            statement.executeUpdate();
            transaction.rollback();
        }

        try (var connection = DatabaseConnection.openConnection(databaseFile);
             var resultSet = connection.createStatement().executeQuery("SELECT COUNT(*) FROM banks")) {
            assertTrue(resultSet.next());
            assertEquals(0, resultSet.getInt(1));
        }
    }

    private Path initializedDatabase(String fileName) throws Exception {
        Path databaseFile = temporaryDirectory.resolve(fileName);
        DatabaseInitializer.initialize(databaseFile);
        assertTrue(databaseFile.toFile().exists());
        assertFalse(databaseFile.toFile().isDirectory());
        return databaseFile;
    }
}