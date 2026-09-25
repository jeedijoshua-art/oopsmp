package com.corebanking.transaction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.exception.InsufficientFundsException;
import com.corebanking.model.Bank;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.LedgerRepository;
import com.corebanking.repository.TransactionRepository;
import com.corebanking.service.AccountService;
import com.corebanking.service.AccountServiceImpl;
import com.corebanking.service.LedgerServiceImpl;
import com.corebanking.service.TransactionService;
import com.corebanking.service.TransactionServiceImpl;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConcurrencyIntegrationTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 10, 30);

    @TempDir
    Path temporaryDirectory;

    private Path databaseFile;
    private AccountLockManager lockManager;
    private Connection setupConnection;

    @BeforeEach
    void setUp() throws Exception {
        databaseFile = temporaryDirectory.resolve("concurrency.db");
        DatabaseInitializer.initialize(databaseFile);
        setupConnection = DatabaseConnection.openConnection(databaseFile);
        new BankRepository(setupConnection).save(new Bank(1L, "BANK-A", "Test Bank", CREATED_AT));
        lockManager = new AccountLockManager();
    }

    @AfterEach
    void tearDown() throws Exception {
        setupConnection.close();
    }

    @Test
    void concurrentWithdrawalsCannotOverdrawAccount() throws Exception {
        createAccount("ACC-A", new BigDecimal("10000.00"));
        List<Throwable> failures = new ArrayList<>();
        List<Transaction> successful = runConcurrent(20, () -> service().withdraw("ACC-A",
                new BigDecimal("1000.00"), "concurrent withdrawal"), failures);

        assertEquals(10, successful.size());
        assertEquals(new BigDecimal("0.00"), readBalance("ACC-A"));
        assertTrue(failures.stream().allMatch(failure -> failure instanceof InsufficientFundsException));
        assertEquals(successful.size(), new HashSet<>(successful.stream()
                .map(Transaction::getTransactionId).toList()).size());
        assertAllSuccessfulTransactionsBalanced();
    }

    @Test
    void concurrentDepositsDoNotLoseUpdates() throws Exception {
        createAccount("ACC-A", BigDecimal.ZERO);
        List<Throwable> failures = new ArrayList<>();
        List<Transaction> successful = runConcurrent(100, () -> service().deposit("ACC-A",
                new BigDecimal("100.00"), "concurrent deposit"), failures);

        assertEquals(100, successful.size());
        assertTrue(failures.isEmpty());
        assertEquals(new BigDecimal("10000.00"), readBalance("ACC-A"));
        assertAllSuccessfulTransactionsBalanced();
    }

    @Test
    void oppositeDirectionTransfersCompleteWithoutDeadlock() throws Exception {
        createAccount("ACC-A", new BigDecimal("10000.00"));
        createAccount("ACC-B", new BigDecimal("10000.00"));
        List<Throwable> failures = new ArrayList<>();
        List<Transaction> successful = runConcurrent(20, () -> {
            int workerNumber = Integer.parseInt(Thread.currentThread().getName()
                    .substring("worker-".length()));
            String source = workerNumber % 2 == 0 ? "ACC-A" : "ACC-B";
            String destination = source.equals("ACC-A") ? "ACC-B" : "ACC-A";
            return service().transfer(source, destination, new BigDecimal("1000.00"), "opposite transfer");
        }, failures);

        assertTrue(failures.isEmpty(), failures.toString());
        assertEquals(20, successful.size());
        assertTrue(failures.isEmpty());
        assertEquals(new BigDecimal("20000.00"), readBalance("ACC-A").add(readBalance("ACC-B")));
        assertAllSuccessfulTransactionsBalanced();
    }

    @Test
    void concurrentTransfersConserveMoneyAcrossThreeAccounts() throws Exception {
        createAccount("ACC-A", new BigDecimal("100000.00"));
        createAccount("ACC-B", new BigDecimal("100000.00"));
        createAccount("ACC-C", new BigDecimal("100000.00"));
        String[][] pairs = {{"ACC-A", "ACC-B"}, {"ACC-B", "ACC-C"}, {"ACC-C", "ACC-A"}};
        List<Throwable> failures = new ArrayList<>();
        List<Transaction> successful = runConcurrent(60, () -> {
            int index = Integer.parseInt(Thread.currentThread().getName().substring("worker-".length()));
            String[] pair = pairs[index % pairs.length];
            return service().transfer(pair[0], pair[1], new BigDecimal("100.00"), "conservation");
        }, failures);

        assertEquals(60, successful.size());
        assertTrue(failures.isEmpty());
        assertEquals(new BigDecimal("300000.00"), readBalance("ACC-A")
                .add(readBalance("ACC-B")).add(readBalance("ACC-C")));
        assertAllSuccessfulTransactionsBalanced();
    }

    @Test
    void lockIsReleasedAfterFailureAndSuccess() throws Exception {
        createAccount("ACC-A", new BigDecimal("100.00"));
        try (var statement = setupConnection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_once BEFORE INSERT ON transactions "
                    + "WHEN NEW.description = 'forced' "
                    + "BEGIN SELECT RAISE(ABORT, 'forced failure'); END");
        }

        try {
            service().deposit("ACC-A", new BigDecimal("10.00"), "forced");
        } catch (RuntimeException ignored) {
            // The assertion below proves the lock was not retained.
        }
        Transaction transaction = service().deposit("ACC-A", new BigDecimal("10.00"), "after failure");
        assertEquals(TransactionStatus.SUCCESS, transaction.getStatus());
        assertEquals(new BigDecimal("110.00"), readBalance("ACC-A"));

        Transaction second = service().deposit("ACC-A", new BigDecimal("10.00"), "after success");
        assertNotEquals(transaction.getTransactionId(), second.getTransactionId());
    }

    @Test
    void lockManagerOrdersMultipleAccountsAndUsesSeparateLocks() {
        var first = lockManager.getLock("ACC-A");
        var second = lockManager.getLock("ACC-B");
        assertNotEquals(first, second);
        try (AccountLockManager.LockScope ignored = lockManager.lockAccounts("ACC-B", "ACC-A")) {
            assertTrue(first.isHeldByCurrentThread());
            assertTrue(second.isHeldByCurrentThread());
        }
        assertTrue(!first.isLocked());
        assertTrue(!second.isLocked());
    }

    private TransactionService service() throws Exception {
        Connection connection = DatabaseConnection.openConnection(databaseFile);
        AccountService accountService = new AccountServiceImpl(new AccountRepository(connection),
                new BankRepository(connection));
        return new ClosingTransactionService(new TransactionServiceImpl(connection, accountService,
                new AccountRepository(connection), new TransactionRepository(connection),
                new LedgerServiceImpl(new LedgerRepository(connection), new TransactionRepository(connection)),
                lockManager), connection);
    }

    private void createAccount(String accountId, BigDecimal balance) {
        AccountService accountService = new AccountServiceImpl(new AccountRepository(setupConnection),
                new BankRepository(setupConnection));
        accountService.createAccount(accountId, "Customer " + accountId, 1L, AccountType.SAVINGS, balance);
    }

    private BigDecimal readBalance(String accountId) throws Exception {
        try (Connection connection = DatabaseConnection.openConnection(databaseFile)) {
            return new AccountRepository(connection).findById(accountId).orElseThrow().getBalance();
        }
    }

    private List<Transaction> runConcurrent(int count, Task task, List<Throwable> failures) throws Exception {
        AtomicInteger workerNumber = new AtomicInteger();
        ExecutorService executor = Executors.newFixedThreadPool(count, runnable -> {
            Thread thread = new Thread(runnable);
            thread.setName("worker-" + workerNumber.getAndIncrement());
            return thread;
        });
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Transaction>> futures = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            futures.add(executor.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Workers did not start together.");
                }
                try {
                    return task.run();
                } catch (Throwable failure) {
                    synchronized (failures) {
                        failures.add(failure);
                    }
                    return null;
                }
            }));
        }
        assertTrue(ready.await(10, TimeUnit.SECONDS));
        start.countDown();
        List<Transaction> successful = new ArrayList<>();
        try {
            for (Future<Transaction> future : futures) {
                Transaction transaction = future.get(30, TimeUnit.SECONDS);
                if (transaction != null) {
                    successful.add(transaction);
                }
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        return successful;
    }

    private void assertAllSuccessfulTransactionsBalanced() throws Exception {
        try (Connection connection = DatabaseConnection.openConnection(databaseFile)) {
            TransactionRepository transactions = new TransactionRepository(connection);
            LedgerRepository ledger = new LedgerRepository(connection);
            for (Transaction transaction : transactions.findAll()) {
                List<com.corebanking.model.LedgerEntry> entries = ledger.findByTransactionId(transaction.getTransactionId());
                assertEquals(transaction.getAmount(), entries
                        .stream().filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                        .map(entry -> entry.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));
                assertEquals(transaction.getAmount(), entries
                    .stream().filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                    .map(entry -> entry.getAmount()).reduce(BigDecimal.ZERO, BigDecimal::add));
            }
        }
    }

    @FunctionalInterface
    private interface Task {
        Transaction run() throws Exception;
    }

    private static final class ClosingTransactionService implements TransactionService {
        private final TransactionService delegate;
        private final Connection connection;

        private ClosingTransactionService(TransactionService delegate, Connection connection) {
            this.delegate = delegate;
            this.connection = connection;
        }

        @Override
        public Transaction deposit(String accountId, BigDecimal amount, String description) {
            try {
                return delegate.deposit(accountId, amount, description);
            } finally {
                close();
            }
        }

        @Override
        public Transaction withdraw(String accountId, BigDecimal amount, String description) {
            try {
                return delegate.withdraw(accountId, amount, description);
            } finally {
                close();
            }
        }

        @Override
        public Transaction transfer(String fromAccountId, String toAccountId, BigDecimal amount, String description) {
            try {
                return delegate.transfer(fromAccountId, toAccountId, amount, description);
            } finally {
                close();
            }
        }

        private void close() {
            try {
                connection.close();
            } catch (Exception ignored) {
                // The operation result is already determined.
            }
        }
    }
}
