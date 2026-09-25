package com.corebanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.InsufficientFundsException;
import com.corebanking.exception.InvalidAccountStateException;
import com.corebanking.exception.InvalidAmountException;
import com.corebanking.exception.TransactionFailedException;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.TransactionRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransactionServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 10, 30);

    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private AccountService accountService;
    private TransactionRepository transactionRepository;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("transaction-service.db");
        DatabaseInitializer.initialize(databaseFile);
        connection = DatabaseConnection.openConnection(databaseFile);
        new BankRepository(connection).save(new Bank(1L, "BANK-A", "Test Bank", CREATED_AT));
        AccountRepository accountRepository = new AccountRepository(connection);
        accountService = new AccountServiceImpl(accountRepository, new BankRepository(connection));
        transactionRepository = new TransactionRepository(connection);
        transactionService = new TransactionServiceImpl(connection, accountService);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void successfulDepositIncreasesBalanceAndCreatesSuccessTransaction() {
        createAccount("ACC100001", new BigDecimal("10000.00"));

        Transaction transaction = transactionService.deposit("ACC100001", new BigDecimal("2000.00"), "Deposit");

        assertEquals(new BigDecimal("12000.00"), accountService.getBalance("ACC100001"));
        assertEquals(TransactionType.DEPOSIT, transaction.getTransactionType());
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals("ACC100001", transaction.getToAccount());
        assertTrue(transactionRepository.findById(transaction.getTransactionId()).isPresent());
    }

    @Test
    void successfulWithdrawalDecreasesBalanceAndCreatesSuccessTransaction() {
        createAccount("ACC100001", new BigDecimal("12000.00"));

        Transaction transaction = transactionService.withdraw("ACC100001", new BigDecimal("3000.00"), "Withdrawal");

        assertEquals(new BigDecimal("9000.00"), accountService.getBalance("ACC100001"));
        assertEquals(TransactionType.WITHDRAWAL, transaction.getTransactionType());
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals("ACC100001", transaction.getFromAccount());
        assertTrue(transactionRepository.findById(transaction.getTransactionId()).isPresent());
    }

    @Test
    void rejectsZeroAndNegativeAmounts() {
        createAccount("ACC100001", new BigDecimal("100.00"));

        assertThrows(InvalidAmountException.class,
                () -> transactionService.deposit("ACC100001", BigDecimal.ZERO, "invalid"));
        assertThrows(InvalidAmountException.class,
                () -> transactionService.deposit("ACC100001", new BigDecimal("-1.00"), "invalid"));
        assertThrows(InvalidAmountException.class,
                () -> transactionService.withdraw("ACC100001", BigDecimal.ZERO, "invalid"));
        assertThrows(InvalidAmountException.class,
                () -> transactionService.withdraw("ACC100001", new BigDecimal("-1.00"), "invalid"));
    }

    @Test
    void rejectsInsufficientFundsWithoutChangingState() {
        createAccount("ACC100001", new BigDecimal("5000.00"));

        assertThrows(InsufficientFundsException.class,
                () -> transactionService.withdraw("ACC100001", new BigDecimal("7000.00"), "invalid"));

        assertEquals(new BigDecimal("5000.00"), accountService.getBalance("ACC100001"));
        assertTrue(transactionRepository.findAll().isEmpty());
    }

    @Test
    void rejectsInactiveAndClosedAccounts() {
        createAccount("ACC100001", BigDecimal.ZERO);
        accountService.deactivateAccount("ACC100001");
        assertThrows(InvalidAccountStateException.class,
                () -> transactionService.deposit("ACC100001", new BigDecimal("1.00"), "inactive"));
        assertThrows(InvalidAccountStateException.class,
                () -> transactionService.withdraw("ACC100001", new BigDecimal("1.00"), "inactive"));

        accountService.activateAccount("ACC100001");
        accountService.closeAccount("ACC100001");
        assertThrows(InvalidAccountStateException.class,
                () -> transactionService.deposit("ACC100001", new BigDecimal("1.00"), "closed"));
        assertThrows(InvalidAccountStateException.class,
                () -> transactionService.withdraw("ACC100001", new BigDecimal("1.00"), "closed"));
    }

    @Test
    void rejectsMissingAccount() {
        assertThrows(com.corebanking.exception.AccountNotFoundException.class,
                () -> transactionService.deposit("missing", new BigDecimal("1.00"), "missing"));
    }

    @Test
    void preservesLargeBigDecimalExactlyAndGeneratesUniqueIds() {
        createAccount("ACC100001", new BigDecimal("12345678901234567890.12"));

        Transaction first = transactionService.deposit("ACC100001", new BigDecimal("0.88"), "large");
        Transaction second = transactionService.withdraw("ACC100001", new BigDecimal("1.00"), "large");

        assertEquals(new BigDecimal("12345678901234567890.00"), accountService.getBalance("ACC100001"));
        assertNotEquals(first.getTransactionId(), second.getTransactionId());
        assertEquals(2, transactionRepository.findByAccountId("ACC100001").size());
    }

    @Test
    void rollsBackBalanceWhenTransactionInsertFails() throws Exception {
        createAccount("ACC100001", new BigDecimal("100.00"));
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TRIGGER fail_transaction_insert BEFORE INSERT ON transactions "
                        + "BEGIN SELECT RAISE(ABORT, 'forced transaction failure'); END")) {
            statement.execute();
        }

        assertThrows(TransactionFailedException.class,
                () -> transactionService.deposit("ACC100001", new BigDecimal("25.00"), "forced failure"));

        assertEquals(new BigDecimal("100.00"), accountService.getBalance("ACC100001"));
        assertTrue(transactionRepository.findAll().isEmpty());
    }

    @Test
    void leavesNoTransactionWhenBalanceUpdateFails() throws Exception {
        createAccount("ACC100001", new BigDecimal("100.00"));
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TRIGGER fail_balance_update BEFORE UPDATE OF balance ON accounts "
                        + "BEGIN SELECT RAISE(ABORT, 'forced balance failure'); END")) {
            statement.execute();
        }

        assertThrows(TransactionFailedException.class,
                () -> transactionService.withdraw("ACC100001", new BigDecimal("25.00"), "forced failure"));

        assertEquals(new BigDecimal("100.00"), accountService.getBalance("ACC100001"));
        assertTrue(transactionRepository.findAll().isEmpty());
    }

    private void createAccount(String accountId, BigDecimal balance) {
        accountService.createAccount(accountId, "Test Customer", 1L, AccountType.SAVINGS, balance);
    }
}
