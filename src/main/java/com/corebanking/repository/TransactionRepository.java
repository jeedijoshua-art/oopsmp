package com.corebanking.repository;

import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.model.Transaction;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class TransactionRepository {
    private final Connection connection;

    public TransactionRepository(Connection connection) {
        this.connection = requireConnection(connection);
    }

    public void save(Transaction transaction) {
        String sql = "INSERT INTO transactions (transaction_id, transaction_type, from_account, "
                + "to_account, amount, status, description, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindTransaction(statement, transaction);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to save transaction: " + transaction.getTransactionId(), exception);
        }
    }

    public Optional<Transaction> findById(String transactionId) {
        String sql = "SELECT transaction_id, transaction_type, from_account, to_account, amount, status, "
                + "description, created_at FROM transactions WHERE transaction_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, transactionId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find transaction: " + transactionId, exception);
        }
    }

    public List<Transaction> findAll() {
        String sql = "SELECT transaction_id, transaction_type, from_account, to_account, amount, status, "
                + "description, created_at FROM transactions ORDER BY created_at, transaction_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return mapRows(resultSet);
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find all transactions.", exception);
        }
    }

    public List<Transaction> findByAccountId(String accountId) {
        String sql = "SELECT transaction_id, transaction_type, from_account, to_account, amount, status, "
                + "description, created_at FROM transactions WHERE from_account = ? OR to_account = ? "
                + "ORDER BY created_at, transaction_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            statement.setString(2, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find transactions for account: " + accountId, exception);
        }
    }

    public void updateStatus(String transactionId, TransactionStatus status) {
        String sql = "UPDATE transactions SET status = ? WHERE transaction_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, transactionId);
            if (statement.executeUpdate() == 0) {
                throw new DataAccessException("Transaction was not found for status update: " + transactionId, null);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to update transaction status: " + transactionId, exception);
        }
    }

    private void bindTransaction(PreparedStatement statement, Transaction transaction) throws SQLException {
        statement.setString(1, transaction.getTransactionId());
        statement.setString(2, transaction.getTransactionType().name());
        statement.setString(3, transaction.getFromAccount());
        statement.setString(4, transaction.getToAccount());
        statement.setString(5, transaction.getAmount().toPlainString());
        statement.setString(6, transaction.getStatus().name());
        statement.setString(7, transaction.getDescription());
        statement.setString(8, JdbcValueMapper.writeDateTime(transaction.getCreatedAt()));
    }

    private Transaction mapRow(ResultSet resultSet) throws SQLException {
        return new Transaction(resultSet.getString("transaction_id"),
                TransactionType.valueOf(resultSet.getString("transaction_type")),
                resultSet.getString("from_account"), resultSet.getString("to_account"),
                JdbcValueMapper.readMoney(resultSet, "amount"),
                TransactionStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("description"),
                JdbcValueMapper.readDateTime(resultSet, "created_at"));
    }

    private List<Transaction> mapRows(ResultSet resultSet) throws SQLException {
        List<Transaction> transactions = new ArrayList<>();
        while (resultSet.next()) {
            transactions.add(mapRow(resultSet));
        }
        return transactions;
    }

    private static Connection requireConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Database connection is required.");
        }
        return connection;
    }
}
