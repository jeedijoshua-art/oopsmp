package com.corebanking.service;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

public final class DatabaseManagementServiceImpl implements DatabaseManagementService {
    private final Connection connection;

    public DatabaseManagementServiceImpl(Connection connection) {
        this.connection = connection;
    }

    @Override
    public void resetDemoData() {
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("DELETE FROM ledger_entries WHERE is_demo = 1");
                statement.execute("DELETE FROM transactions WHERE is_demo = 1");
                statement.execute("DELETE FROM accounts WHERE is_demo = 1");
                statement.execute("DELETE FROM banks WHERE is_demo = 1");
            }
            connection.commit();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("Failed to reset demo data", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override
    public void resetEntireDatabase() {
        try {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("PRAGMA foreign_keys = ON;");
                statement.execute("DELETE FROM ledger_entries");
                statement.execute("DELETE FROM transactions");
                statement.execute("DELETE FROM accounts WHERE account_id <> 'SYSTEM-CASH'");
                statement.execute("DELETE FROM banks WHERE bank_code <> 'SYSTEM'");
            }
            connection.commit();
        } catch (SQLException exception) {
            try { connection.rollback(); } catch (SQLException ignored) {}
            throw new RuntimeException("Failed to reset entire database", exception);
        } finally {
            try { connection.setAutoCommit(true); } catch (SQLException ignored) {}
        }
    }

    @Override
    public int getDemoAccountCount() {
        return getCount("SELECT COUNT(*) FROM accounts WHERE is_demo = 1");
    }

    @Override
    public int getDemoTransactionCount() {
        return getCount("SELECT COUNT(*) FROM transactions WHERE is_demo = 1");
    }

    private int getCount(String sql) {
        try (Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery(sql)) {
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return 0;
    }
}
