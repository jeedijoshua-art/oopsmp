package com.corebanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.InsufficientFundsException;
import com.corebanking.exception.InvalidAccountStateException;
import com.corebanking.exception.InvalidAmountException;
import com.corebanking.exception.InvalidTransferException;
import com.corebanking.exception.TransactionFailedException;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.ReconciliationReport;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.LedgerRepository;
import com.corebanking.repository.TransactionRepository;
import com.corebanking.transaction.AccountLockManager;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Phase 8 Comprehensive Core Banking Engine Quality & Concurrency Audit Test Suite.
 *
 * Verifies the 16 core banking requirements:
 * 1. Valid deposit
 * 2. Valid withdrawal
 * 3. Insufficient funds rejection
 * 4. Invalid amounts (zero & negative)
 * 5. Inactive and closed accounts rejection
 * 6. Intra-bank transfers
 * 7. Inter-bank transfers
 * 8. Self-transfer rejection
 * 9. Balanced debit and credit entries verification
 * 10. Rollback after failure injection (atomicity verification)
 * 11. Concurrent withdrawals against limited funds
 * 12. Concurrent transfers across accounts
 * 13. Unique transaction IDs
 * 14. Persistence after reopening database connection
 * 15. Receipt data matching persisted transaction
 * 16. Ledger reconciliation detecting deliberately introduced discrepancies
 */
class CoreBankingEngineAuditTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 29, 8, 0);

    @TempDir
    Path tempDir;

    private Path dbPath;
    private AccountLockManager lockManager;
    private Connection initConn;

    @BeforeEach
    void setUp() throws Exception {
        dbPath = tempDir.resolve("audit-engine.db");
        DatabaseInitializer.initialize(dbPath);
        initConn = DatabaseConnection.openConnection(dbPath);
        BankRepository bankRepo = new BankRepository(initConn);
        bankRepo.save(new Bank(1L, "APEX-001", "Apex National Bank", NOW));
        bankRepo.save(new Bank(2L, "METRO-002", "Metro Commercial Bank", NOW));
        lockManager = new AccountLockManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (initConn != null && !initConn.isClosed()) {
            initConn.close();
        }
    }

    private void seedAccount(String accountId, Long bankId, String customer, BigDecimal initialBalance, AccountStatus status) throws Exception {
        Account account = new Account(
                accountId,
                customer,
                30,
                customer.toLowerCase().replace(" ", "") + "@bank.com",
                "555-0100",
                bankId,
                AccountType.SAVINGS,
                initialBalance,
                initialBalance,
                status,
                NOW,
                NOW
        );
        new AccountRepository(initConn).save(account);
    }

    private TransactionServiceImpl createService(Connection conn) {
        AccountRepository accountRepo = new AccountRepository(conn);
        BankRepository bankRepo = new BankRepository(conn);
        AccountService accountService = new AccountServiceImpl(accountRepo, bankRepo);
        TransactionRepository txRepo = new TransactionRepository(conn);
        LedgerRepository ledgerRepo = new LedgerRepository(conn);
        LedgerService ledgerService = new LedgerServiceImpl(ledgerRepo, txRepo);
        return new TransactionServiceImpl(conn, accountService, accountRepo, txRepo, ledgerService, lockManager);
    }

    // -------------------------------------------------------------------------
    // Scenario 1: Valid deposit
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 1: Valid deposit updates balance, creates transaction and balanced ledger entries")
    void scenario01_validDeposit() throws Exception {
        seedAccount("ACC-DEP-1", 1L, "Alice Johnson", new BigDecimal("100.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            Transaction tx = service.deposit("ACC-DEP-1", new BigDecimal("50.00"), "ATM Deposit");

            assertNotNull(tx.getTransactionId());
            assertEquals(TransactionType.DEPOSIT, tx.getTransactionType());
            assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
            assertEquals(new BigDecimal("50.00"), tx.getAmount());

            Account updated = new AccountRepository(conn).findById("ACC-DEP-1").orElseThrow();
            assertEquals(new BigDecimal("150.00"), updated.getBalance());

            List<LedgerEntry> entries = new LedgerRepository(conn).findByTransactionId(tx.getTransactionId());
            assertEquals(2, entries.size());
            BigDecimal debits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("50.00"), debits);
            assertEquals(new BigDecimal("50.00"), credits);
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 2: Valid withdrawal
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 2: Valid withdrawal decreases balance and logs balanced ledger entries")
    void scenario02_validWithdrawal() throws Exception {
        seedAccount("ACC-WTH-1", 1L, "Bob Smith", new BigDecimal("200.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            Transaction tx = service.withdraw("ACC-WTH-1", new BigDecimal("75.00"), "Branch Cash Withdrawal");

            assertNotNull(tx);
            assertEquals(TransactionStatus.SUCCESS, tx.getStatus());
            Account updated = new AccountRepository(conn).findById("ACC-WTH-1").orElseThrow();
            assertEquals(new BigDecimal("125.00"), updated.getBalance());

            List<LedgerEntry> entries = new LedgerRepository(conn).findByTransactionId(tx.getTransactionId());
            assertEquals(2, entries.size());
            BigDecimal debits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("75.00"), debits);
            assertEquals(new BigDecimal("75.00"), credits);
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 3: Insufficient funds rejection
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 3: Insufficient funds rejection leaves balance, transactions, and ledger untouched")
    void scenario03_insufficientFundsRejection() throws Exception {
        seedAccount("ACC-ISF-1", 1L, "Charlie", new BigDecimal("50.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            assertThrows(InsufficientFundsException.class, () ->
                    service.withdraw("ACC-ISF-1", new BigDecimal("100.00"), "Overdraft attempt"));

            Account acc = new AccountRepository(conn).findById("ACC-ISF-1").orElseThrow();
            assertEquals(new BigDecimal("50.00"), acc.getBalance());
            assertTrue(new TransactionRepository(conn).findAll().isEmpty());
            assertTrue(new LedgerRepository(conn).findAll().isEmpty());
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 4: Invalid amounts (zero and negative)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 4: Zero and negative amounts are rejected immediately")
    void scenario04_invalidAmounts() throws Exception {
        seedAccount("ACC-INV-1", 1L, "David", new BigDecimal("100.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-INV-2", 1L, "David 2", new BigDecimal("100.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            assertThrows(InvalidAmountException.class, () ->
                    service.deposit("ACC-INV-1", BigDecimal.ZERO, "Zero deposit"));
            assertThrows(InvalidAmountException.class, () ->
                    service.deposit("ACC-INV-1", new BigDecimal("-20.00"), "Negative deposit"));
            assertThrows(InvalidAmountException.class, () ->
                    service.withdraw("ACC-INV-1", BigDecimal.ZERO, "Zero withdraw"));
            assertThrows(InvalidAmountException.class, () ->
                    service.withdraw("ACC-INV-1", new BigDecimal("-10.00"), "Negative withdraw"));
            assertThrows(InvalidAmountException.class, () ->
                    service.transfer("ACC-INV-1", "ACC-INV-2", new BigDecimal("-5.00"), "Negative transfer"));

            Account acc = new AccountRepository(conn).findById("ACC-INV-1").orElseThrow();
            assertEquals(new BigDecimal("100.00"), acc.getBalance());
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 5: Inactive and closed accounts rejection
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 5: Transactions on inactive or closed accounts are rejected")
    void scenario05_inactiveAndClosedAccountsRejection() throws Exception {
        seedAccount("ACC-INACT", 1L, "Inactive User", new BigDecimal("500.00"), AccountStatus.INACTIVE);
        seedAccount("ACC-CLOSED", 1L, "Closed User", new BigDecimal("0.00"), AccountStatus.CLOSED);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            assertThrows(InvalidAccountStateException.class, () ->
                    service.deposit("ACC-INACT", new BigDecimal("50.00"), "Deposit to inactive"));
            assertThrows(InvalidAccountStateException.class, () ->
                    service.withdraw("ACC-INACT", new BigDecimal("50.00"), "Withdraw from inactive"));
            assertThrows(InvalidAccountStateException.class, () ->
                    service.deposit("ACC-CLOSED", new BigDecimal("50.00"), "Deposit to closed"));
            assertThrows(InvalidAccountStateException.class, () ->
                    service.withdraw("ACC-CLOSED", new BigDecimal("50.00"), "Withdraw from closed"));
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 6: Intra-bank transfers (same bank)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 6: Intra-bank transfer transfers funds and produces balanced journal entries")
    void scenario06_intraBankTransfer() throws Exception {
        seedAccount("ACC-INTRA-A", 1L, "Priya Patel", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-INTRA-B", 1L, "Rohan Sharma", new BigDecimal("200.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            Transaction tx = service.transfer("ACC-INTRA-A", "ACC-INTRA-B", new BigDecimal("300.00"), "Peer transfer");

            assertNotNull(tx);
            assertEquals(TransactionType.TRANSFER, tx.getTransactionType());
            assertEquals(TransactionStatus.SUCCESS, tx.getStatus());

            Account from = new AccountRepository(conn).findById("ACC-INTRA-A").orElseThrow();
            Account to = new AccountRepository(conn).findById("ACC-INTRA-B").orElseThrow();
            assertEquals(new BigDecimal("700.00"), from.getBalance());
            assertEquals(new BigDecimal("500.00"), to.getBalance());

            List<LedgerEntry> entries = new LedgerRepository(conn).findByTransactionId(tx.getTransactionId());
            assertEquals(2, entries.size());
            BigDecimal debits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("300.00"), debits);
            assertEquals(new BigDecimal("300.00"), credits);
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 7: Inter-bank transfers (different banks)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 7: Inter-bank transfer transfers between distinct financial institutions")
    void scenario07_interBankTransfer() throws Exception {
        seedAccount("ACC-BANK-1", 1L, "Apex Customer", new BigDecimal("5000.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-BANK-2", 2L, "Metro Customer", new BigDecimal("1000.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            Transaction tx = service.transfer("ACC-BANK-1", "ACC-BANK-2", new BigDecimal("1500.00"), "Inter-bank settlement");

            Account from = new AccountRepository(conn).findById("ACC-BANK-1").orElseThrow();
            Account to = new AccountRepository(conn).findById("ACC-BANK-2").orElseThrow();
            assertEquals(new BigDecimal("3500.00"), from.getBalance());
            assertEquals(new BigDecimal("2500.00"), to.getBalance());

            // Verify they belong to different banks
            assertFalse(from.getBankId().equals(to.getBankId()));

            List<LedgerEntry> entries = new LedgerRepository(conn).findByTransactionId(tx.getTransactionId());
            assertEquals(2, entries.size());
            BigDecimal debits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertEquals(new BigDecimal("1500.00"), debits);
            assertEquals(new BigDecimal("1500.00"), credits);
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 8: Self-transfer rejection (same account)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 8: Transfer to the same account is rejected with InvalidTransferException")
    void scenario08_selfTransferRejection() throws Exception {
        seedAccount("ACC-SELF", 1L, "Self User", new BigDecimal("500.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            assertThrows(InvalidTransferException.class, () ->
                    service.transfer("ACC-SELF", "ACC-SELF", new BigDecimal("100.00"), "Self transfer"));

            Account acc = new AccountRepository(conn).findById("ACC-SELF").orElseThrow();
            assertEquals(new BigDecimal("500.00"), acc.getBalance());
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 9: Balanced debit and credit entries verification
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 9: All operations (deposit, withdrawal, transfer) maintain strict debit=credit invariant")
    void scenario09_balancedDebitAndCreditEntries() throws Exception {
        seedAccount("ACC-BAL-1", 1L, "User One", new BigDecimal("2000.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-BAL-2", 2L, "User Two", new BigDecimal("2000.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            Transaction t1 = service.deposit("ACC-BAL-1", new BigDecimal("450.00"), "Deposit 1");
            Transaction t2 = service.withdraw("ACC-BAL-1", new BigDecimal("120.00"), "Withdrawal 1");
            Transaction t3 = service.transfer("ACC-BAL-1", "ACC-BAL-2", new BigDecimal("800.00"), "Transfer 1");

            LedgerRepository ledger = new LedgerRepository(conn);
            for (Transaction t : List.of(t1, t2, t3)) {
                List<LedgerEntry> entries = ledger.findByTransactionId(t.getTransactionId());
                BigDecimal debits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal credits = entries.stream().filter(e -> e.getEntryType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
                assertEquals(debits, credits, "Debits must equal credits for transaction " + t.getTransactionId());
                assertEquals(t.getAmount(), debits);
            }
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 10: Rollback after failure injection (atomicity verification)
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 10: Rollback on failure leaves zero partial balance, transaction, or ledger mutations")
    void scenario10_rollbackAfterFailureInjection() throws Exception {
        seedAccount("ACC-ROLL-1", 1L, "Rollback Source", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-ROLL-2", 2L, "Rollback Dest", new BigDecimal("500.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            AccountRepository accountRepo = new AccountRepository(conn);
            TransactionRepository txRepo = new TransactionRepository(conn);
            LedgerRepository ledgerRepo = new LedgerRepository(conn);
            TransactionServiceImpl service = createService(conn);

            // Test failure after balance update
            service.setFailureInjector((point, tx) -> {
                if (point == TransactionServiceImpl.FailurePoint.AFTER_BALANCE_UPDATE) {
                    throw new RuntimeException("Simulated crash right after balance update");
                }
            });

            assertThrows(TransactionFailedException.class, () ->
                    service.transfer("ACC-ROLL-1", "ACC-ROLL-2", new BigDecimal("200.00"), "Failing transfer 1"));

            // Verify balances and tables are unchanged
            assertEquals(new BigDecimal("1000.00"), accountRepo.findById("ACC-ROLL-1").orElseThrow().getBalance());
            assertEquals(new BigDecimal("500.00"), accountRepo.findById("ACC-ROLL-2").orElseThrow().getBalance());
            assertTrue(txRepo.findAll().isEmpty());
            assertTrue(ledgerRepo.findAll().isEmpty());

            // Test failure after transaction save
            service.setFailureInjector((point, tx) -> {
                if (point == TransactionServiceImpl.FailurePoint.AFTER_TRANSACTION_SAVE) {
                    throw new RuntimeException("Simulated crash after transaction save");
                }
            });

            assertThrows(TransactionFailedException.class, () ->
                    service.deposit("ACC-ROLL-1", new BigDecimal("300.00"), "Failing deposit"));

            assertEquals(new BigDecimal("1000.00"), accountRepo.findById("ACC-ROLL-1").orElseThrow().getBalance());
            assertTrue(txRepo.findAll().isEmpty());
            assertTrue(ledgerRepo.findAll().isEmpty());

            // Test failure after ledger save
            service.setFailureInjector((point, tx) -> {
                if (point == TransactionServiceImpl.FailurePoint.AFTER_LEDGER_SAVE) {
                    throw new RuntimeException("Simulated crash after ledger save");
                }
            });

            assertThrows(TransactionFailedException.class, () ->
                    service.withdraw("ACC-ROLL-1", new BigDecimal("100.00"), "Failing withdraw"));

            assertEquals(new BigDecimal("1000.00"), accountRepo.findById("ACC-ROLL-1").orElseThrow().getBalance());
            assertTrue(txRepo.findAll().isEmpty());
            assertTrue(ledgerRepo.findAll().isEmpty());

            // Remove failure injector and confirm subsequent operation succeeds cleanly
            service.setFailureInjector(null);
            Transaction cleanTx = service.deposit("ACC-ROLL-1", new BigDecimal("100.00"), "Clean deposit");
            assertNotNull(cleanTx);
            assertEquals(new BigDecimal("1100.00"), accountRepo.findById("ACC-ROLL-1").orElseThrow().getBalance());
            assertEquals(1, txRepo.findAll().size());
            assertEquals(2, ledgerRepo.findAll().size());
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 11: Concurrent withdrawals against limited funds
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 11: Concurrent withdrawals cannot overdraw account (strict race condition prevention)")
    void scenario11_concurrentWithdrawalsAgainstLimitedFunds() throws Exception {
        seedAccount("ACC-CONC-WTH", 1L, "Limited Funds Customer", new BigDecimal("500.00"), AccountStatus.ACTIVE);

        int threadCount = 20;
        BigDecimal withdrawalAmount = new BigDecimal("50.00"); // 10 can succeed, 10 must fail
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
                    TransactionService service = createService(conn);
                    service.withdraw("ACC-CONC-WTH", withdrawalAmount, "Concurrent ATM cash-out");
                    return true;
                } catch (InsufficientFundsException isf) {
                    return false;
                }
            }));
        }

        assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
        startLatch.countDown();

        int successes = 0;
        int failures = 0;
        for (Future<Boolean> future : futures) {
            if (future.get(10, TimeUnit.SECONDS)) {
                successes++;
            } else {
                failures++;
            }
        }
        executor.shutdown();

        assertEquals(10, successes, "Exactly 10 withdrawals of 50.00 should succeed out of 500.00 balance");
        assertEquals(10, failures, "Exactly 10 withdrawals should fail due to insufficient funds");

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            Account acc = new AccountRepository(conn).findById("ACC-CONC-WTH").orElseThrow();
            assertEquals(new BigDecimal("0.00"), acc.getBalance());
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 12: Concurrent transfers across accounts
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 12: Concurrent bidirectional transfers preserve total system liquidity without deadlocks")
    void scenario12_concurrentTransfersConservation() throws Exception {
        seedAccount("ACC-XFER-1", 1L, "Partner A", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-XFER-2", 2L, "Partner B", new BigDecimal("1000.00"), AccountStatus.ACTIVE);

        int cycles = 30;
        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(cycles * 2);

        for (int i = 0; i < cycles; i++) {
            executor.submit(() -> {
                try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
                    TransactionService service = createService(conn);
                    service.transfer("ACC-XFER-1", "ACC-XFER-2", new BigDecimal("10.00"), "Transfer A -> B");
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
            executor.submit(() -> {
                try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
                    TransactionService service = createService(conn);
                    service.transfer("ACC-XFER-2", "ACC-XFER-1", new BigDecimal("10.00"), "Transfer B -> A");
                } catch (Exception ignored) {
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(15, TimeUnit.SECONDS));
        executor.shutdown();

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            Account acc1 = new AccountRepository(conn).findById("ACC-XFER-1").orElseThrow();
            Account acc2 = new AccountRepository(conn).findById("ACC-XFER-2").orElseThrow();
            BigDecimal totalMoney = acc1.getBalance().add(acc2.getBalance());
            assertEquals(new BigDecimal("2000.00"), totalMoney, "Total liquidity must be strictly conserved at 2000.00");
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 13: Unique transaction IDs
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 13: High volume transaction generation produces globally unique transaction IDs")
    void scenario13_uniqueTransactionIds() throws Exception {
        seedAccount("ACC-UNQ-1", 1L, "Unique ID User", new BigDecimal("50000.00"), AccountStatus.ACTIVE);

        Set<String> generatedIds = Collections.synchronizedSet(new HashSet<>());
        int count = 50;
        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            for (int i = 0; i < count; i++) {
                Transaction tx = service.deposit("ACC-UNQ-1", new BigDecimal("1.00"), "Uniq test " + i);
                assertTrue(generatedIds.add(tx.getTransactionId()), "Transaction ID must be globally unique: " + tx.getTransactionId());
            }
        }
        assertEquals(count, generatedIds.size());
    }

    // -------------------------------------------------------------------------
    // Scenario 14: Persistence after reopening database connection
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 14: Reopening database connection proves durability of accounts, transactions, and ledger")
    void scenario14_persistenceAcrossConnectionCycles() throws Exception {
        seedAccount("ACC-PERST-1", 1L, "Durable User", new BigDecimal("500.00"), AccountStatus.ACTIVE);

        String txId;
        try (Connection conn1 = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service1 = createService(conn1);
            Transaction tx = service1.deposit("ACC-PERST-1", new BigDecimal("250.00"), "Durable deposit");
            txId = tx.getTransactionId();
        }

        // Open completely new connection to verify disk durability
        try (Connection conn2 = DatabaseConnection.openConnection(dbPath)) {
            Account acc = new AccountRepository(conn2).findById("ACC-PERST-1").orElseThrow();
            assertEquals(new BigDecimal("750.00"), acc.getBalance());

            Transaction tx = new TransactionRepository(conn2).findById(txId).orElseThrow();
            assertEquals(new BigDecimal("250.00"), tx.getAmount());
            assertEquals(TransactionStatus.SUCCESS, tx.getStatus());

            List<LedgerEntry> entries = new LedgerRepository(conn2).findByTransactionId(txId);
            assertEquals(2, entries.size());
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 15: Receipt data matching persisted transaction
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 15: Transaction receipt data precisely reflects persisted transaction and confirmed balances")
    void scenario15_receiptDataMatching() throws Exception {
        seedAccount("ACC-RCPT-SRC", 1L, "Sender", new BigDecimal("1200.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-RCPT-DST", 2L, "Receiver", new BigDecimal("300.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            TransactionService service = createService(conn);
            Transaction tx = service.transfer("ACC-RCPT-SRC", "ACC-RCPT-DST", new BigDecimal("450.00"), "Vendor payment");

            Account updatedSrc = new AccountRepository(conn).findById("ACC-RCPT-SRC").orElseThrow();
            Account updatedDst = new AccountRepository(conn).findById("ACC-RCPT-DST").orElseThrow();

            assertEquals("ACC-RCPT-SRC", tx.getFromAccount());
            assertEquals("ACC-RCPT-DST", tx.getToAccount());
            assertEquals(new BigDecimal("450.00"), tx.getAmount());
            assertEquals(new BigDecimal("750.00"), updatedSrc.getBalance());
            assertEquals(new BigDecimal("750.00"), updatedDst.getBalance());

            // Check ledger receipt breakdown
            List<LedgerEntry> entries = new LedgerRepository(conn).findByTransactionId(tx.getTransactionId());
            assertEquals(2, entries.size());
            assertTrue(entries.stream().anyMatch(e -> e.getAccountId().equals("ACC-RCPT-SRC") && e.getEntryType() == LedgerEntryType.DEBIT));
            assertTrue(entries.stream().anyMatch(e -> e.getAccountId().equals("ACC-RCPT-DST") && e.getEntryType() == LedgerEntryType.CREDIT));
        }
    }

    // -------------------------------------------------------------------------
    // Scenario 16: Ledger reconciliation detecting discrepancies
    // -------------------------------------------------------------------------
    @Test
    @DisplayName("Scenario 16: Ledger reconciliation engine detects balanced states and catches deliberate tampering")
    void scenario16_ledgerReconciliationAndDiscrepancyDetection() throws Exception {
        seedAccount("ACC-RECON-1", 1L, "Clean Account", new BigDecimal("1000.00"), AccountStatus.ACTIVE);
        seedAccount("ACC-RECON-2", 2L, "Tampered Account", new BigDecimal("1000.00"), AccountStatus.ACTIVE);

        try (Connection conn = DatabaseConnection.openConnection(dbPath)) {
            AccountRepository accRepo = new AccountRepository(conn);
            TransactionRepository txRepo = new TransactionRepository(conn);
            LedgerRepository ledgerRepo = new LedgerRepository(conn);
            LedgerService ledgerService = new LedgerServiceImpl(ledgerRepo, txRepo);
            TransactionService txService = createService(conn);

            // Execute genuine transactions
            txService.deposit("ACC-RECON-1", new BigDecimal("200.00"), "Deposit 1");
            txService.transfer("ACC-RECON-1", "ACC-RECON-2", new BigDecimal("300.00"), "Transfer 1");

            // Run reconciliation audit on genuine state
            List<Account> accounts = accRepo.findAll();
            ReconciliationReport cleanReport = ledgerService.generateReconciliationReport(accounts, id -> "Bank " + id);

            assertTrue(cleanReport.isJournalBalanced(), "Journal must be balanced");
            assertTrue(cleanReport.isFullyReconciled(), "Clean report must be fully reconciled");
            assertEquals(0, cleanReport.discrepancyCount());
            assertFalse(cleanReport.hasDiscrepancies());

            // Deliberately tamper with ACC-RECON-2's balance directly in SQL to simulate out-of-band balance corruption
            try (PreparedStatement stmt = conn.prepareStatement("UPDATE accounts SET balance = '9999.00' WHERE account_id = 'ACC-RECON-2'")) {
                stmt.executeUpdate();
            }

            // Re-run audit
            List<Account> tamperedAccounts = accRepo.findAll();
            ReconciliationReport tamperedReport = ledgerService.generateReconciliationReport(tamperedAccounts, id -> "Bank " + id);

            assertTrue(tamperedReport.isJournalBalanced(), "Journal entries remain internally balanced");
            assertFalse(tamperedReport.isFullyReconciled(), "Report must flag reconciliation failure");
            assertTrue(tamperedReport.hasDiscrepancies());
            assertEquals(1, tamperedReport.discrepancyCount(), "Exactly 1 discrepancy should be detected");

            var badRecord = tamperedReport.accountReconciliations().stream()
                    .filter(r -> r.accountId().equals("ACC-RECON-2"))
                    .findFirst()
                    .orElseThrow();

            assertFalse(badRecord.isReconciled());
            // Expected balance according to ledger: initial 1000.00 + credit 300.00 = 1300.00
            assertEquals(new BigDecimal("1300.00"), badRecord.ledgerDerivedBalance());
            assertEquals(new BigDecimal("9999.00"), badRecord.actualBalance());
            // Discrepancy: actual (9999.00) - ledger (1300.00) = 8699.00
            assertEquals(new BigDecimal("8699.00"), badRecord.discrepancy());
        }
    }
}
