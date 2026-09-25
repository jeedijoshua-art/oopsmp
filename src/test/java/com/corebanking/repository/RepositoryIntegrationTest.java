package com.corebanking.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RepositoryIntegrationTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 10, 30);

    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private BankRepository bankRepository;
    private AccountRepository accountRepository;
    private TransactionRepository transactionRepository;
    private LedgerRepository ledgerRepository;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("repository-test.db");
        DatabaseInitializer.initialize(databaseFile);
        connection = DatabaseConnection.openConnection(databaseFile);
        bankRepository = new BankRepository(connection);
        accountRepository = new AccountRepository(connection);
        transactionRepository = new TransactionRepository(connection);
        ledgerRepository = new LedgerRepository(connection);
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void bankRepositorySavesFindsAndListsBanks() {
        Bank bank = bank(1L, "BANK-A");
        bankRepository.save(bank);

        assertEquals(bank, bankRepository.findById(1L).orElseThrow());
        assertEquals(bank, bankRepository.findByCode("BANK-A").orElseThrow());
        assertTrue(bankRepository.existsById(1L));
        assertEquals(List.of(bank), bankRepository.findAll());
        assertTrue(bankRepository.findById(99L).isEmpty());
    }

    @Test
    void bankRepositoryWrapsDuplicateConstraint() {
        bankRepository.save(bank(1L, "BANK-A"));

        assertThrows(DataAccessException.class, () -> bankRepository.save(bank(2L, "BANK-A")));
    }

    @Test
    void accountRepositorySavesFindsUpdatesAndFiltersByBank() {
        bankRepository.save(bank(1L, "BANK-A"));
        bankRepository.save(bank(2L, "BANK-B"));
        Account account = account("A001", 1L, new BigDecimal("100.00"), AccountStatus.ACTIVE);
        accountRepository.save(account);
        accountRepository.save(account("B001", 2L, new BigDecimal("50.00"), AccountStatus.ACTIVE));

        assertAccountEquals(account, accountRepository.findById("A001").orElseThrow());
        assertTrue(accountRepository.existsById("A001"));
        assertEquals(2, accountRepository.findAll().size());
        assertEquals(List.of("A001"), accountRepository.findByBankId(1L).stream()
            .map(Account::getAccountId).toList());
        assertTrue(accountRepository.findById("missing").isEmpty());

        Account updated = account("A001", 1L, new BigDecimal("125.00"), AccountStatus.INACTIVE);
        accountRepository.update(updated);
        assertAccountEquals(updated, accountRepository.findById("A001").orElseThrow());
    }

    @Test
    void accountRepositoryRejectsUpdateForMissingAccount() {
        bankRepository.save(bank(1L, "BANK-A"));

        assertThrows(DataAccessException.class,
                () -> accountRepository.update(account("missing", 1L, BigDecimal.ZERO, AccountStatus.ACTIVE)));
    }

    @Test
    void transactionRepositorySavesFindsListsFiltersAndUpdatesStatus() {
        bankRepository.save(bank(1L, "BANK-A"));
        accountRepository.save(account("A001", 1L, new BigDecimal("100.00"), AccountStatus.ACTIVE));
        Transaction transaction = transaction("TXN-001", "A001", null, TransactionType.WITHDRAWAL);
        transactionRepository.save(transaction);

        assertTransactionEquals(transaction, transactionRepository.findById("TXN-001").orElseThrow());
        assertEquals(1, transactionRepository.findAll().size());
        assertEquals(List.of("TXN-001"), transactionRepository.findByAccountId("A001").stream()
            .map(Transaction::getTransactionId).toList());
        assertTrue(transactionRepository.findById("missing").isEmpty());

        transactionRepository.updateStatus("TXN-001", TransactionStatus.SUCCESS);
        assertEquals(TransactionStatus.SUCCESS,
                transactionRepository.findById("TXN-001").orElseThrow().getStatus());
    }

    @Test
    void ledgerRepositorySavesFindsAndListsEntries() {
        bankRepository.save(bank(1L, "BANK-A"));
        accountRepository.save(account("A001", 1L, new BigDecimal("100.00"), AccountStatus.ACTIVE));
        accountRepository.save(account("A002", 1L, new BigDecimal("0.00"), AccountStatus.ACTIVE));
        transactionRepository.save(transaction("TXN-001", "A001", "A002", TransactionType.TRANSFER));
        List<LedgerEntry> entries = List.of(
                entry(1L, "TXN-001", "A001", LedgerEntryType.DEBIT),
                entry(2L, "TXN-001", "A002", LedgerEntryType.CREDIT));

        ledgerRepository.save(entries.get(0));
        ledgerRepository.saveAll(List.of(entries.get(1)));

        assertLedgerEntriesEqual(entries, ledgerRepository.findByTransactionId("TXN-001"));
        assertLedgerEntriesEqual(List.of(entries.get(0)), ledgerRepository.findByAccountId("A001"));
        assertLedgerEntriesEqual(entries, ledgerRepository.findAll());
    }

    @Test
    void repositoriesPersistForeignKeyRelationshipChain() {
        Bank bank = bank(1L, "BANK-A");
        Account account = account("A001", 1L, new BigDecimal("100.00"), AccountStatus.ACTIVE);
        Transaction transaction = transaction("TXN-001", "A001", null, TransactionType.WITHDRAWAL);
        LedgerEntry entry = entry(1L, "TXN-001", "A001", LedgerEntryType.DEBIT);

        bankRepository.save(bank);
        accountRepository.save(account);
        transactionRepository.save(transaction);
        ledgerRepository.save(entry);

        assertEquals(bank, bankRepository.findById(1L).orElseThrow());
        assertAccountEquals(account, accountRepository.findById("A001").orElseThrow());
        assertTransactionEquals(transaction, transactionRepository.findById("TXN-001").orElseThrow());
        assertLedgerEntryEquals(entry, ledgerRepository.findByTransactionId("TXN-001").get(0));
    }

    @Test
    void repositoryDoesNotControlExternalCommitOrRollback() throws Exception {
        connection.setAutoCommit(false);
        bankRepository.save(bank(1L, "BANK-R"));
        connection.rollback();
        connection.setAutoCommit(true);
        assertFalse(bankRepository.findById(1L).isPresent());

        connection.setAutoCommit(false);
        bankRepository.save(bank(2L, "BANK-C"));
        connection.commit();
        connection.setAutoCommit(true);
        assertTrue(bankRepository.findById(2L).isPresent());
    }

    private Bank bank(Long bankId, String bankCode) {
        return new Bank(bankId, bankCode, "Test " + bankCode, CREATED_AT);
    }

    private Account account(String accountId, Long bankId, BigDecimal balance, AccountStatus status) {
        return new Account(accountId, "Customer " + accountId, bankId, AccountType.SAVINGS,
                balance, status, CREATED_AT, CREATED_AT);
    }

    private Transaction transaction(String transactionId, String fromAccount, String toAccount,
                                    TransactionType transactionType) {
        return new Transaction(transactionId, transactionType, fromAccount, toAccount,
                new BigDecimal("25.00"), TransactionStatus.PENDING, "Repository test", CREATED_AT);
    }

    private LedgerEntry entry(Long entryId, String transactionId, String accountId,
                              LedgerEntryType entryType) {
        return new LedgerEntry(entryId, transactionId, accountId, entryType,
                new BigDecimal("25.00"), "Repository test", CREATED_AT);
    }

    private void assertAccountEquals(Account expected, Account actual) {
        assertEquals(expected.getAccountId(), actual.getAccountId());
        assertEquals(expected.getCustomerName(), actual.getCustomerName());
        assertEquals(expected.getBankId(), actual.getBankId());
        assertEquals(expected.getAccountType(), actual.getAccountType());
        assertEquals(expected.getBalance(), actual.getBalance());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getCreatedAt(), actual.getCreatedAt());
        assertEquals(expected.getUpdatedAt(), actual.getUpdatedAt());
    }

    private void assertTransactionEquals(Transaction expected, Transaction actual) {
        assertEquals(expected.getTransactionId(), actual.getTransactionId());
        assertEquals(expected.getTransactionType(), actual.getTransactionType());
        assertEquals(expected.getFromAccount(), actual.getFromAccount());
        assertEquals(expected.getToAccount(), actual.getToAccount());
        assertEquals(expected.getAmount(), actual.getAmount());
        assertEquals(expected.getStatus(), actual.getStatus());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getCreatedAt(), actual.getCreatedAt());
    }

    private void assertLedgerEntriesEqual(List<LedgerEntry> expected, List<LedgerEntry> actual) {
        assertEquals(expected.size(), actual.size());
        for (int index = 0; index < expected.size(); index++) {
            assertLedgerEntryEquals(expected.get(index), actual.get(index));
        }
    }

    private void assertLedgerEntryEquals(LedgerEntry expected, LedgerEntry actual) {
        assertEquals(expected.getLedgerEntryId(), actual.getLedgerEntryId());
        assertEquals(expected.getTransactionId(), actual.getTransactionId());
        assertEquals(expected.getAccountId(), actual.getAccountId());
        assertEquals(expected.getEntryType(), actual.getEntryType());
        assertEquals(expected.getAmount(), actual.getAmount());
        assertEquals(expected.getDescription(), actual.getDescription());
        assertEquals(expected.getCreatedAt(), actual.getCreatedAt());
    }
}
