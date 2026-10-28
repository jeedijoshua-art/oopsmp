package com.corebanking.service;

import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.exception.AccountClosureException;
import com.corebanking.exception.AccountNotFoundException;
import com.corebanking.exception.DataAccessException;
import com.corebanking.exception.DuplicateAccountException;
import com.corebanking.exception.InvalidAccountDataException;
import com.corebanking.exception.InvalidAccountStateException;
import com.corebanking.model.Account;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;

import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public final class AccountServiceImpl implements AccountService {
    private final AccountRepository accountRepository;
    private final BankRepository bankRepository;

    public AccountServiceImpl(Connection connection) {
        this(new AccountRepository(connection), new BankRepository(connection));
    }

    public AccountServiceImpl(AccountRepository accountRepository, BankRepository bankRepository) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "Account repository is required.");
        this.bankRepository = Objects.requireNonNull(bankRepository, "Bank repository is required.");
    }

    @Override
    public Account createAccount(String customerName, Integer age, String email, String phoneNumber, Long bankId,
                                 AccountType accountType, BigDecimal initialBalance) {
        requireText(customerName, "Customer name");
        if (age == null || age < 18 || age > 120) {
            throw new InvalidAccountDataException("Age must be between 18 and 120.");
        }
        requireText(email, "Email");
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new InvalidAccountDataException("Invalid email format.");
        }
        requireText(phoneNumber, "Phone number");
        if (!phoneNumber.matches("^\\+?[0-9]{10,15}$")) {
            throw new InvalidAccountDataException("Invalid phone number format.");
        }
        if (bankId == null || bankId <= 0 || !bankRepository.existsById(bankId)) {
            throw new InvalidAccountDataException("Bank does not exist: " + bankId);
        }
        if (accountType == null) {
            throw new InvalidAccountDataException("Account type is required.");
        }
        if (initialBalance == null || initialBalance.signum() < 0) {
            throw new InvalidAccountDataException("Initial balance must be zero or greater.");
        }

        String accountId;
        int attempts = 0;
        do {
            accountId = "ACC-" + java.util.UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
            attempts++;
            if (attempts > 10) {
                throw new IllegalStateException("Failed to generate a unique account ID.");
            }
        } while (accountRepository.existsById(accountId));

        LocalDateTime now = LocalDateTime.now();
        Account account;
        try {
            account = new Account(accountId, customerName, age, email, phoneNumber, bankId, accountType,
                    initialBalance, AccountStatus.ACTIVE, now, now);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAccountDataException(exception.getMessage());
        }
        try {
            accountRepository.save(account);
            return account;
        } catch (DataAccessException exception) {
            throw exception;
        }
    }

    @Override
    public Account createAccount(String accountId, String customerName, Long bankId,
                                 AccountType accountType, BigDecimal initialBalance) {
        requireText(accountId, "Account ID");
        requireText(customerName, "Customer name");
        if (!isValidAccountId(accountId)) {
            throw new InvalidAccountDataException("Account ID contains invalid characters: " + accountId);
        }
        if (bankId == null || bankId <= 0 || !bankRepository.existsById(bankId)) {
            throw new InvalidAccountDataException("Bank does not exist: " + bankId);
        }
        if (accountType == null) {
            throw new InvalidAccountDataException("Account type is required.");
        }
        if (initialBalance == null || initialBalance.signum() < 0) {
            throw new InvalidAccountDataException("Initial balance must be zero or greater.");
        }
        if (accountRepository.existsById(accountId)) {
            throw new DuplicateAccountException(accountId);
        }

        LocalDateTime now = LocalDateTime.now();
        Account account;
        try {
            account = new Account(accountId, customerName, null, null, null, bankId, accountType,
                    initialBalance, AccountStatus.ACTIVE, now, now);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAccountDataException(exception.getMessage());
        }
        try {
            accountRepository.save(account);
            return account;
        } catch (DataAccessException exception) {
            throw exception;
        }
    }

    @Override
    public Account findAccount(String accountId) {
        requireText(accountId, "Account ID");
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    @Override
    public List<Account> listAccounts() {
        return accountRepository.findAll();
    }

    @Override
    public List<Account> searchAccounts(String searchTerm) {
        requireText(searchTerm, "Search term");
        String normalizedTerm = searchTerm.toLowerCase(Locale.ROOT);
        return listAccounts().stream()
                .filter(account -> account.getAccountId().toLowerCase(Locale.ROOT).contains(normalizedTerm)
                        || account.getCustomerName().toLowerCase(Locale.ROOT).contains(normalizedTerm))
                .toList();
    }

    @Override
    public Account updateAccount(String accountId, String customerName, AccountType accountType) {
        Account existing = findAccount(accountId);
        requireText(customerName, "Customer name");
        if (accountType == null) {
            throw new InvalidAccountDataException("Account type is required.");
        }
        Account updated = new Account(existing.getAccountId(), customerName, existing.getAge(), existing.getEmail(), existing.getPhoneNumber(), existing.getBankId(),
                accountType, existing.getBalance(), existing.getStatus(), existing.getCreatedAt(),
                LocalDateTime.now());
        accountRepository.update(updated);
        return updated;
    }

    @Override
    public Account activateAccount(String accountId) {
        Account account = findAccount(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidAccountStateException("Closed account cannot be activated: " + accountId);
        }
        if (account.getStatus() == AccountStatus.ACTIVE) {
            return account;
        }
        return updateStatus(account, AccountStatus.ACTIVE);
    }

    @Override
    public Account deactivateAccount(String accountId) {
        Account account = findAccount(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new InvalidAccountStateException("Closed account cannot be deactivated: " + accountId);
        }
        if (account.getStatus() == AccountStatus.INACTIVE) {
            return account;
        }
        return updateStatus(account, AccountStatus.INACTIVE);
    }

    @Override
    public Account closeAccount(String accountId) {
        Account account = findAccount(accountId);
        if (account.getStatus() == AccountStatus.CLOSED) {
            throw new AccountClosureException("Account is already closed: " + accountId);
        }
        if (account.getBalance().signum() != 0) {
            throw new AccountClosureException("Account balance must be zero before closure: " + accountId);
        }
        return updateStatus(account, AccountStatus.CLOSED);
    }

    @Override
    public void deleteAccount(String accountId) {
        Account account = findAccount(accountId);
        if (account.getStatus() != AccountStatus.CLOSED) {
            throw new InvalidAccountStateException("Account must be closed before it can be deleted.");
        }
        accountRepository.delete(accountId);
    }

    @Override
    public boolean accountExists(String accountId) {
        requireText(accountId, "Account ID");
        return accountRepository.existsById(accountId);
    }

    @Override
    public BigDecimal getBalance(String accountId) {
        return findAccount(accountId).getBalance();
    }

    @Override
    public void validateAccountCanTransact(String accountId) {
        Account account = findAccount(accountId);
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new InvalidAccountStateException("Account is not active: " + accountId);
        }
    }

    private Account updateStatus(Account account, AccountStatus status) {
        Account updated = new Account(account.getAccountId(), account.getCustomerName(), account.getAge(), account.getEmail(), account.getPhoneNumber(), account.getBankId(),
                account.getAccountType(), account.getBalance(), status, account.getCreatedAt(),
                LocalDateTime.now());
        accountRepository.update(updated);
        return updated;
    }

    private static void requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new InvalidAccountDataException(fieldName + " is required.");
        }
    }

    private static boolean isValidAccountId(String accountId) {
        return accountId.matches("[A-Za-z0-9_-]+");
    }
}
