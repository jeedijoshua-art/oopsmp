package com.corebanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.AccountNotFoundException;
import com.corebanking.exception.DataAccessException;
import com.corebanking.exception.InsufficientFundsException;
import com.corebanking.exception.InvalidAccountStateException;
import com.corebanking.exception.InvalidAmountException;
import com.corebanking.exception.InvalidTransferException;
import com.corebanking.exception.TransactionFailedException;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.LedgerRepository;
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

class TransferServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 10, 30);

    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private AccountService accountService;
    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private LedgerRepository ledgerRepository;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("transfer-service.db");
        DatabaseInitializer.initialize(databaseFile);
        connection = DatabaseConnection.openConnection(databaseFile);
        new BankRepository(connection).save(new Bank(1L, "BANK-A", "Test Bank", CREATED_AT));
        accountRepository = new AccountRepository(connection);
        accountService = new AccountServiceImpl(accountRepository, new BankRepository(connection));
        transactionRepository = new TransactionRepository(connection);
        ledgerRepository = new LedgerRepository(connection);
        transactionService = new TransactionServiceImpl(connection, accountService);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void successfulTransferMovesMoneyAndCreatesExactlyTwoBalancedEntries() {
        createAccount("ACC1001", new BigDecimal("10000.00"));
        createAccount("ACC1002", new BigDecimal("5000.00"));

        Transaction transaction = transactionService.transfer("ACC1001", "ACC1002",
                new BigDecimal("3000.00"), "transfer");
        List<LedgerEntry> entries = ledgerRepository.findByTransactionId(transaction.getTransactionId());

        assertEquals(new BigDecimal("7000.00"), accountService.getBalance("ACC1001"));
        assertEquals(new BigDecimal("8000.00"), accountService.getBalance("ACC1002"));
        assertEquals(TransactionType.TRANSFER, transaction.getTransactionType());
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertTrue(transactionRepository.findById(transaction.getTransactionId()).isPresent());
        assertEquals(2, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals("ACC1001", entries.get(0).getAccountId());
        assertEquals("ACC1002", entries.get(1).getAccountId());
        assertEquals(transaction.getTransactionId(), entries.get(0).getTransactionId());
        assertEquals(transaction.getTransactionId(), entries.get(1).getTransactionId());
        assertEquals(entries.get(0).getAmount(), entries.get(1).getAmount());
        assertEquals("transfer", transaction.getDescription());
    }

    @Test
    void transferPreservesTotalBalanceAndExactLargePrecision() {
        BigDecimal sourceInitial = new BigDecimal("12345678901234567890.12");
        BigDecimal destinationInitial = new BigDecimal("5.88");
        BigDecimal amount = new BigDecimal("0.12");
        createAccount("ACC1001", sourceInitial);
        createAccount("ACC1002", destinationInitial);

        transactionService.transfer("ACC1001", "ACC1002", amount, "exact");

        BigDecimal before = sourceInitial.add(destinationInitial);
        BigDecimal after = accountService.getBalance("ACC1001").add(accountService.getBalance("ACC1002"));
        assertEquals(0, before.compareTo(after));
        assertEquals(new BigDecimal("12345678901234567890.00"), accountService.getBalance("ACC1001"));
        assertEquals(new BigDecimal("6.00"), accountService.getBalance("ACC1002"));
    }

    @Test
    void transferIdsAreUniqueAndTimestampIsPersisted() {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1002", BigDecimal.ZERO);

        Transaction first = transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("10.00"), "one");
        Transaction second = transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("10.00"), "two");

        assertNotEquals(first.getTransactionId(), second.getTransactionId());
        assertTrue(first.getCreatedAt() != null);
        assertTrue(transactionRepository.findById(second.getTransactionId()).orElseThrow().getCreatedAt() != null);
    }

    @Test
    void rejectsInvalidTransferInputsWithoutDatabaseChanges() {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1002", BigDecimal.ZERO);

        assertThrows(InvalidTransferException.class,
                () -> transactionService.transfer(null, "ACC1002", new BigDecimal("1.00"), null));
        assertThrows(InvalidTransferException.class,
                () -> transactionService.transfer("ACC1001", null, new BigDecimal("1.00"), null));
        assertThrows(InvalidTransferException.class,
                () -> transactionService.transfer("ACC1001", "ACC1001", new BigDecimal("1.00"), null));
        assertThrows(InvalidAmountException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", BigDecimal.ZERO, null));
        assertThrows(InvalidAmountException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("-1.00"), null));
        assertThrows(InvalidAmountException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", null, null));

        assertEquals(new BigDecimal("100.00"), accountService.getBalance("ACC1001"));
        assertEquals(BigDecimal.ZERO.setScale(2), accountService.getBalance("ACC1002"));
        assertTrue(transactionRepository.findAll().isEmpty());
        assertTrue(ledgerRepository.findAll().isEmpty());
    }

    @Test
    void rejectsMissingAndInsufficientAccounts() {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1001X", BigDecimal.ZERO);

        assertThrows(AccountNotFoundException.class,
                () -> transactionService.transfer("missing", "ACC1001", new BigDecimal("1.00"), null));
        assertThrows(AccountNotFoundException.class,
                () -> transactionService.transfer("ACC1001", "missing", new BigDecimal("1.00"), null));
        assertThrows(InsufficientFundsException.class,
                () -> transactionService.transfer("ACC1001", "ACC1001X", new BigDecimal("101.00"), null));
        assertTrue(transactionRepository.findAll().isEmpty());
    }

    @Test
    void rejectsInactiveAndClosedSourceOrDestination() {
        createAccount("ACC1001", BigDecimal.ZERO);
        createAccount("ACC1002", BigDecimal.ZERO);
        accountService.deactivateAccount("ACC1001");
        assertThrows(InvalidAccountStateException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("1.00"), null));

        accountService.activateAccount("ACC1001");
        accountService.deactivateAccount("ACC1002");
        assertThrows(InvalidAccountStateException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("1.00"), null));

        createAccount("ACC1003", BigDecimal.ZERO);
        accountService.closeAccount("ACC1003");
        assertThrows(InvalidAccountStateException.class,
                () -> transactionService.transfer("ACC1003", "ACC1002", new BigDecimal("1.00"), null));

        createAccount("ACC1004", BigDecimal.ZERO);
        accountService.closeAccount("ACC1004");
        assertThrows(InvalidAccountStateException.class,
            () -> transactionService.transfer("ACC1001", "ACC1004", new BigDecimal("1.00"), null));
    }

    @Test
    void sourceUpdateFailureRollsBackEverything() throws Exception {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1002", BigDecimal.ZERO);
        createUpdateFailureTrigger("ACC1001");

        assertThrows(TransactionFailedException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("10.00"), "failure"));
        assertConsistentOriginalState("ACC1001", "ACC1002", new BigDecimal("100.00"), BigDecimal.ZERO);
    }

    @Test
    void destinationUpdateFailureRollsBackEverything() throws Exception {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1002", BigDecimal.ZERO);
        createUpdateFailureTrigger("ACC1002");

        assertThrows(TransactionFailedException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("10.00"), "failure"));
        assertConsistentOriginalState("ACC1001", "ACC1002", new BigDecimal("100.00"), BigDecimal.ZERO);
    }

    @Test
    void transactionInsertFailureRollsBackEverything() throws Exception {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1002", BigDecimal.ZERO);
        execute("CREATE TRIGGER fail_transfer_transaction BEFORE INSERT ON transactions "
                + "WHEN NEW.transaction_type = 'TRANSFER' "
                + "BEGIN SELECT RAISE(ABORT, 'forced transaction failure'); END");

        assertThrows(TransactionFailedException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("10.00"), "failure"));
        assertConsistentOriginalState("ACC1001", "ACC1002", new BigDecimal("100.00"), BigDecimal.ZERO);
    }

    @Test
    void creditLedgerFailureRollsBackEverything() throws Exception {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1002", BigDecimal.ZERO);
        execute("CREATE TRIGGER fail_transfer_credit BEFORE INSERT ON ledger_entries "
                + "WHEN NEW.account_id = 'ACC1002' "
                + "BEGIN SELECT RAISE(ABORT, 'forced credit failure'); END");

        assertThrows(TransactionFailedException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("10.00"), "failure"));
        assertConsistentOriginalState("ACC1001", "ACC1002", new BigDecimal("100.00"), BigDecimal.ZERO);
    }

    @Test
    void ledgerBalanceValidationFailureRollsBackEverything() throws Exception {
        createAccount("ACC1001", new BigDecimal("100.00"));
        createAccount("ACC1002", BigDecimal.ZERO);
        LedgerService failingLedgerService = new FailingValidationLedgerService(
                new LedgerServiceImpl(ledgerRepository, transactionRepository));
        transactionService = new TransactionServiceImpl(connection, accountService, accountRepository,
                transactionRepository, failingLedgerService);

        assertThrows(TransactionFailedException.class,
                () -> transactionService.transfer("ACC1001", "ACC1002", new BigDecimal("10.00"), "failure"));
        assertConsistentOriginalState("ACC1001", "ACC1002", new BigDecimal("100.00"), BigDecimal.ZERO);
    }

    private void createAccount(String accountId, BigDecimal balance) {
        accountService.createAccount(accountId, "Customer " + accountId, 1L, AccountType.SAVINGS, balance);
    }

    private void createUpdateFailureTrigger(String accountId) throws Exception {
        execute("CREATE TRIGGER fail_transfer_balance BEFORE UPDATE OF balance ON accounts "
                + "WHEN NEW.account_id = '" + accountId + "' "
                + "BEGIN SELECT RAISE(ABORT, 'forced balance failure'); END");
    }

    private void assertConsistentOriginalState(String sourceId, String destinationId,
                                               BigDecimal sourceBalance, BigDecimal destinationBalance) {
        assertEquals(sourceBalance, accountService.getBalance(sourceId));
        assertEquals(destinationBalance.setScale(2), accountService.getBalance(destinationId));
        assertTrue(transactionRepository.findAll().isEmpty());
        assertTrue(ledgerRepository.findAll().isEmpty());
    }

    private void execute(String sql) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.execute();
        }
    }

    private static final class FailingValidationLedgerService implements LedgerService {
        private final LedgerService delegate;

        private FailingValidationLedgerService(LedgerService delegate) {
            this.delegate = delegate;
        }

        @Override
        public List<LedgerEntry> createDepositEntries(Transaction transaction) {
            return delegate.createDepositEntries(transaction);
        }

        @Override
        public List<LedgerEntry> createWithdrawalEntries(Transaction transaction) {
            return delegate.createWithdrawalEntries(transaction);
        }

        @Override
        public List<LedgerEntry> createTransferEntries(Transaction transaction) {
            return delegate.createTransferEntries(transaction);
        }

        @Override
        public void saveEntries(List<LedgerEntry> entries) {
            delegate.saveEntries(entries);
        }

        @Override
        public void validateBalancedEntries(List<LedgerEntry> entries) {
            throw new DataAccessException("Forced ledger validation failure.", null);
        }

        @Override
        public void validateBalancedTransaction(String transactionId) {
            throw new DataAccessException("Forced ledger validation failure.", null);
        }

        @Override
        public BigDecimal totalDebits(String transactionId) {
            return delegate.totalDebits(transactionId);
        }

        @Override
        public BigDecimal totalCredits(String transactionId) {
            return delegate.totalCredits(transactionId);
        }
    }
}
