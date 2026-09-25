package com.corebanking.repository;

import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.model.Account;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class AccountRepository {
    private final Connection connection;

    public AccountRepository(Connection connection) {
        this.connection = requireConnection(connection);
    }

    public void save(Account account) {
        String sql = "INSERT INTO accounts (account_id, customer_name, bank_id, account_type, "
                + "balance, status, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bindAccount(statement, account);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to save account: " + account.getAccountId(), exception);
        }
    }

    public Optional<Account> findById(String accountId) {
        String sql = "SELECT account_id, customer_name, bank_id, account_type, balance, status, "
                + "created_at, updated_at FROM accounts WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() ? Optional.of(mapRow(resultSet)) : Optional.empty();
            }
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find account: " + accountId, exception);
        }
    }

    public List<Account> findAll() {
        String sql = "SELECT account_id, customer_name, bank_id, account_type, balance, status, "
            + "created_at, updated_at FROM accounts WHERE account_id <> 'SYSTEM-CASH' ORDER BY account_id";
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            return mapRows(resultSet);
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find all accounts.", exception);
        }
    }

    public boolean existsById(String accountId) {
        String sql = "SELECT 1 FROM accounts WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, accountId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to check account: " + accountId, exception);
        }
    }

    public void update(Account account) {
        String sql = "UPDATE accounts SET customer_name = ?, bank_id = ?, account_type = ?, balance = ?, "
                + "status = ?, created_at = ?, updated_at = ? WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, account.getCustomerName());
            statement.setLong(2, account.getBankId());
            statement.setString(3, account.getAccountType().name());
            statement.setString(4, account.getBalance().toPlainString());
            statement.setString(5, account.getStatus().name());
            statement.setString(6, JdbcValueMapper.writeDateTime(account.getCreatedAt()));
            statement.setString(7, JdbcValueMapper.writeDateTime(account.getUpdatedAt()));
            statement.setString(8, account.getAccountId());
            if (statement.executeUpdate() == 0) {
                throw new DataAccessException("Account was not found for update: " + account.getAccountId(), null);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to update account: " + account.getAccountId(), exception);
        }
    }

    public List<Account> findByBankId(Long bankId) {
        String sql = "SELECT account_id, customer_name, bank_id, account_type, balance, status, "
            + "created_at, updated_at FROM accounts WHERE bank_id = ? "
            + "AND account_id <> 'SYSTEM-CASH' ORDER BY account_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, bankId);
            try (ResultSet resultSet = statement.executeQuery()) {
                return mapRows(resultSet);
            }
        } catch (SQLException | RuntimeException exception) {
            throw new DataAccessException("Failed to find accounts for bank: " + bankId, exception);
        }
    }

    public void updateBalance(String accountId, java.math.BigDecimal newBalance) {
        String sql = "UPDATE accounts SET balance = ?, updated_at = ? WHERE account_id = ?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, newBalance.toPlainString());
            statement.setString(2, JdbcValueMapper.writeDateTime(LocalDateTime.now()));
            statement.setString(3, accountId);
            if (statement.executeUpdate() == 0) {
                throw new DataAccessException("Account was not found for balance update: " + accountId, null);
            }
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to update account balance: " + accountId, exception);
        }
    }

    public void ensureSystemCashAccount() {
        String sql = "INSERT OR IGNORE INTO accounts "
                + "(account_id, customer_name, bank_id, account_type, balance, status) "
                + "SELECT 'SYSTEM-CASH', 'System Cash Account', bank_id, 'CURRENT', '0.00', 'ACTIVE' "
                + "FROM banks WHERE bank_code = 'SYSTEM'";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new DataAccessException("Failed to ensure the system cash account exists.", exception);
        }
    }

    private void bindAccount(PreparedStatement statement, Account account) throws SQLException {
        statement.setString(1, account.getAccountId());
        statement.setString(2, account.getCustomerName());
        statement.setLong(3, account.getBankId());
        statement.setString(4, account.getAccountType().name());
        statement.setString(5, account.getBalance().toPlainString());
        statement.setString(6, account.getStatus().name());
        statement.setString(7, JdbcValueMapper.writeDateTime(account.getCreatedAt()));
        statement.setString(8, JdbcValueMapper.writeDateTime(account.getUpdatedAt()));
    }

    private Account mapRow(ResultSet resultSet) throws SQLException {
        return new Account(resultSet.getString("account_id"), resultSet.getString("customer_name"),
                resultSet.getLong("bank_id"), AccountType.valueOf(resultSet.getString("account_type")),
                JdbcValueMapper.readMoney(resultSet, "balance"),
                AccountStatus.valueOf(resultSet.getString("status")),
                JdbcValueMapper.readDateTime(resultSet, "created_at"),
                JdbcValueMapper.readDateTime(resultSet, "updated_at"));
    }

    private List<Account> mapRows(ResultSet resultSet) throws SQLException {
        List<Account> accounts = new ArrayList<>();
        while (resultSet.next()) {
            accounts.add(mapRow(resultSet));
        }
        return accounts;
    }

    private static Connection requireConnection(Connection connection) {
        if (connection == null) {
            throw new IllegalArgumentException("Database connection is required.");
        }
        return connection;
    }
}
