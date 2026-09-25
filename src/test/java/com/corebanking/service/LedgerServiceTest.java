package com.corebanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.exception.TransactionFailedException;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.LedgerRepository;
import com.corebanking.repository.TransactionRepository;

import java.lang.reflect.Method;
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

class LedgerServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 10, 30);

    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private AccountService accountService;
    private TransactionRepository transactionRepository;
    private LedgerRepository ledgerRepository;
    private LedgerService ledgerService;
    private TransactionService transactionService;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("ledger-service.db");
        DatabaseInitializer.initialize(databaseFile);
        connection = DatabaseConnection.openConnection(databaseFile);
        new BankRepository(connection).save(new Bank(1L, "BANK-A", "Test Bank", CREATED_AT));
        AccountRepository accountRepository = new AccountRepository(connection);
        accountService = new AccountServiceImpl(accountRepository, new BankRepository(connection));
        transactionRepository = new TransactionRepository(connection);
        ledgerRepository = new LedgerRepository(connection);
        ledgerService = new LedgerServiceImpl(ledgerRepository, transactionRepository);
        transactionService = new TransactionServiceImpl(connection, accountService);
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void depositCreatesBalancedSystemDebitAndCustomerCredit() {
        createAccount("ACC100001", new BigDecimal("10000.00"));

        Transaction transaction = transactionService.deposit("ACC100001", new BigDecimal("2000.00"), "deposit");
        List<LedgerEntry> entries = ledgerRepository.findByTransactionId(transaction.getTransactionId());

        assertEquals(2, entries.size());
        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals("SYSTEM-CASH", entries.get(0).getAccountId());
        assertEquals("ACC100001", entries.get(1).getAccountId());
        assertEquals(new BigDecimal("2000.00"), ledgerService.totalDebits(transaction.getTransactionId()));
        assertEquals(new BigDecimal("2000.00"), ledgerService.totalCredits(transaction.getTransactionId()));
        ledgerService.validateBalancedTransaction(transaction.getTransactionId());
    }

    @Test
    void withdrawalCreatesCustomerDebitAndSystemCredit() {
        createAccount("ACC100001", new BigDecimal("10000.00"));

        Transaction transaction = transactionService.withdraw("ACC100001", new BigDecimal("2000.00"), "withdrawal");
        List<LedgerEntry> entries = ledgerRepository.findByTransactionId(transaction.getTransactionId());

        assertEquals(LedgerEntryType.DEBIT, entries.get(0).getEntryType());
        assertEquals(LedgerEntryType.CREDIT, entries.get(1).getEntryType());
        assertEquals("ACC100001", entries.get(0).getAccountId());
        assertEquals("SYSTEM-CASH", entries.get(1).getAccountId());
        assertEquals(0, ledgerService.totalDebits(transaction.getTransactionId())
                .compareTo(ledgerService.totalCredits(transaction.getTransactionId())));
    }

    @Test
    void ledgerAmountsArePositiveAndExact() {
        Transaction transaction = new Transaction("TXN-LEDGER", TransactionType.TRANSFER,
                "ACC100001", "ACC100002", new BigDecimal("12345678901234567890.12"),
                TransactionStatus.SUCCESS, "exact", CREATED_AT);
        List<LedgerEntry> entries = ledgerService.createTransferEntries(transaction);

        assertTrue(entries.stream().allMatch(entry -> entry.getAmount().signum() > 0));
        assertEquals(new BigDecimal("12345678901234567890.12"), entries.get(0).getAmount());
        ledgerService.validateBalancedEntries(entries);
    }

    @Test
    void ledgerEntriesReferenceTheirTransactionAndAccounts() {
        Transaction transaction = new Transaction("TXN-REF", TransactionType.TRANSFER,
                "ACC100001", "ACC100002", new BigDecimal("10.00"),
                TransactionStatus.SUCCESS, "reference", CREATED_AT);
        List<LedgerEntry> entries = ledgerService.createTransferEntries(transaction);

        assertTrue(entries.stream().allMatch(entry -> entry.getTransactionId().equals("TXN-REF")));
        assertEquals(List.of("ACC100001", "ACC100002"),
                entries.stream().map(LedgerEntry::getAccountId).toList());
    }

    @Test
    void unbalancedEntriesAreRejected() {
        Transaction transaction = new Transaction("TXN-BAD", TransactionType.TRANSFER,
                "ACC100001", "ACC100002", new BigDecimal("10.00"),
                TransactionStatus.SUCCESS, "unbalanced", CREATED_AT);
        List<LedgerEntry> entries = List.of(
                new LedgerEntry(1L, transaction.getTransactionId(), "ACC100001", LedgerEntryType.DEBIT,
                        new BigDecimal("10.00"), null, CREATED_AT),
                new LedgerEntry(2L, transaction.getTransactionId(), "ACC100002", LedgerEntryType.CREDIT,
                        new BigDecimal("9.99"), null, CREATED_AT));

        assertThrows(DataAccessException.class, () -> ledgerService.validateBalancedEntries(entries));
    }

    @Test
    void missingTransactionCannotBeValidated() {
        assertThrows(DataAccessException.class,
                () -> ledgerService.validateBalancedTransaction("missing"));
    }

    @Test
    void ledgerEntriesAreImmutableAndAppendOnly() {
        assertEquals(0, java.util.Arrays.stream(LedgerEntry.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("set"))
                .count());
        assertEquals(0, java.util.Arrays.stream(LedgerService.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("delete"))
                .count());
    }

    @Test
    void failedSecondLedgerEntryRollsBackBalanceTransactionAndFirstEntry() throws Exception {
        createAccount("ACC100001", new BigDecimal("10000.00"));
        try (PreparedStatement statement = connection.prepareStatement(
                "CREATE TRIGGER fail_customer_ledger BEFORE INSERT ON ledger_entries "
                        + "WHEN NEW.account_id = 'ACC100001' "
                        + "BEGIN SELECT RAISE(ABORT, 'forced ledger failure'); END")) {
            statement.execute();
        }

        assertThrows(TransactionFailedException.class,
                () -> transactionService.deposit("ACC100001", new BigDecimal("2000.00"), "failure"));

        assertEquals(new BigDecimal("10000.00"), accountService.getBalance("ACC100001"));
        assertTrue(transactionRepository.findAll().isEmpty());
        assertTrue(ledgerRepository.findAll().isEmpty());
    }

    @Test
    void phaseSixDepositAndWithdrawalRemainSuccessfulWithLedger() {
        createAccount("ACC100001", new BigDecimal("100.00"));

        transactionService.deposit("ACC100001", new BigDecimal("25.00"), "deposit");
        transactionService.withdraw("ACC100001", new BigDecimal("10.00"), "withdrawal");

        assertEquals(new BigDecimal("115.00"), accountService.getBalance("ACC100001"));
        assertEquals(2, transactionRepository.findByAccountId("ACC100001").size());
        assertEquals(2, ledgerRepository.findByAccountId("ACC100001").size());
    }

    private void createAccount(String accountId, BigDecimal balance) {
        accountService.createAccount(accountId, "Test Customer", 1L, AccountType.SAVINGS, balance);
    }
}
