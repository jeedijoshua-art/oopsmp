package com.corebanking.repository;

import com.corebanking.enums.LedgerEntryType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.model.LedgerEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class LedgerRepository {
    private final Connection connection;

    public LedgerRepository(Connection connection) {
        this.connection = requireConnection(connection);
    }

    public void save(LedgerEntry entry) {
        String sql = "INSERT INTO ledger_entries (ledger_entry_id, transaction_id, account_id, entry_type, "
                + "amount, description, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindEntry(statement, entry);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to save ledger entry: " + entry.getLedgerEntryId(), exception);
        }
    }

    public void saveAll(List<LedgerEntry> entries) {
        String sql = "INSERT INTO ledger_entries (ledger_entry_id, transaction_id, account_id, entry_type, "
                + "amount, description, created_at) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (LedgerEntry entry : entries) {
                bindEntry(statement, entry);
                statement.addBatch();
            }
            statement.executeBatch();
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to save ledger entries.", exception);
        }
    }

    public long nextLedgerEntryId() {
        String sql = "SELECT COALESCE(MAX(ledger_entry_id), 0) + 1 FROM ledger_entries";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            if (!resultSet.next()) {
                throw new DataAccessException("Failed to generate a ledger entry ID.", null);
            }
            return resultSet.getLong(1);
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to generate a ledger entry ID.", exception);
        }
    }

    public List<LedgerEntry> findByTransactionId(String transactionId) {
        return findBy("transaction_id", transactionId, "transaction");
    }

    public List<LedgerEntry> findByAccountId(String accountId) {
        return findBy("account_id", accountId, "account");
    }

    public List<LedgerEntry> findAll() {
        String sql = "SELECT ledger_entry_id, transaction_id, account_id, entry_type, amount, description, "
                + "created_at FROM ledger_entries ORDER BY ledger_entry_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return mapRows(resultSet);
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find all ledger entries.", exception);
        }
    }

    private List<LedgerEntry> findBy(String columnName, String value, String label) {
        String sql = "SELECT ledger_entry_id, transaction_id, account_id, entry_type, amount, description, "
                + "created_at FROM ledger_entries WHERE " + columnName + " = ? ORDER BY ledger_entry_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find ledger entries for " + label + ": " + value, exception);
        }
    }

    private void bindEntry(PreparedStatement statement, LedgerEntry entry) throws SQLException {
        statement.setLong(1, entry.getLedgerEntryId());
        statement.setString(2, entry.getTransactionId());
        statement.setString(3, entry.getAccountId());
        statement.setString(4, entry.getEntryType().name());
        statement.setString(5, entry.getAmount().toPlainString());
        statement.setString(6, entry.getDescription());
        statement.setString(7, JdbcValueMapper.writeDateTime(entry.getCreatedAt()));
    }

    private LedgerEntry mapRow(ResultSet resultSet) throws SQLException {
        return new LedgerEntry(resultSet.getLong("ledger_entry_id"),
                resultSet.getString("transaction_id"), resultSet.getString("account_id"),
                LedgerEntryType.valueOf(resultSet.getString("entry_type")),
                JdbcValueMapper.readMoney(resultSet, "amount"),
                resultSet.getString("description"),
                JdbcValueMapper.readDateTime(resultSet, "created_at"));
    }

    private List<LedgerEntry> mapRows(ResultSet resultSet) throws SQLException {
        List<LedgerEntry> entries = new ArrayList<>();
        while (resultSet.next()) {
            entries.add(mapRow(resultSet));
        }
        return entries;
    }

    private static Connection requireConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Database connection is required.");
        }
        return connection;
    }
}
