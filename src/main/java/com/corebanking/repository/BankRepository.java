package com.corebanking.repository;

import com.corebanking.exception.DataAccessException;
import com.corebanking.model.Bank;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class BankRepository {
    private final Connection connection;

    public BankRepository(Connection connection) {
        this.connection = requireConnection(connection);
    }

    public void save(Bank bank) {
        String sql = "INSERT INTO banks (bank_id, bank_code, bank_name, created_at) VALUES (?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, bank.getBankId());
            statement.setString(2, bank.getBankCode());
            statement.setString(3, bank.getBankName());
            statement.setString(4, JdbcValueMapper.writeDateTime(bank.getCreatedAt()));
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to save bank: " + bank.getBankId(), exception);
        }
    }

    public Optional<Bank> findById(Long bankId) {
        String sql = "SELECT bank_id, bank_code, bank_name, created_at FROM banks WHERE bank_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, bankId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find bank: " + bankId, exception);
        }
    }

    public Optional<Bank> findByCode(String bankCode) {
        String sql = "SELECT bank_id, bank_code, bank_name, created_at FROM banks WHERE bank_code = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, bankCode);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find bank by code: " + bankCode, exception);
        }
    }

    public List<Bank> findAll() {
        String sql = "SELECT bank_id, bank_code, bank_name, created_at FROM banks "
            + "WHERE bank_code <> 'SYSTEM' ORDER BY bank_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            List<Bank> banks = new ArrayList<>();
            while (resultSet.next()) {
                banks.add(mapRow(resultSet));
            }
            return banks;
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find all banks.", exception);
        }
    }

    public boolean existsById(Long bankId) {
        String sql = "SELECT 1 FROM banks WHERE bank_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, bankId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to check bank: " + bankId, exception);
        }
    }

    public void ensureSystemBank() {
        String sql = "INSERT OR IGNORE INTO banks (bank_id, bank_code, bank_name) "
                + "VALUES (0, 'SYSTEM', 'Core Banking System')";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to ensure the system bank exists.", exception);
        }
    }

    private Bank mapRow(ResultSet resultSet) throws SQLException {
        return new Bank(resultSet.getLong("bank_id"), resultSet.getString("bank_code"),
                resultSet.getString("bank_name"), JdbcValueMapper.readDateTime(resultSet, "created_at"));
    }

    private static Connection requireConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Database connection is required.");
        }
        return connection;
    }
}
