package com.corebanking.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.model.Transaction;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.TransactionRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class IdGeneratorTest {
    private static final Pattern TRANSACTION_ID_PATTERN = Pattern.compile(
            "TXN-\\d{8}-[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}");

    @TempDir
    Path temporaryDirectory;

    @Test
    void generatedIdHasReadableRestartSafeFormat() {
        String transactionId = IdGenerator.nextTransactionId();

        assertTrue(TRANSACTION_ID_PATTERN.matcher(transactionId).matches());
        assertTrue(transactionId.startsWith("TXN-" + LocalDate.now().format(java.time.format.DateTimeFormatter.BASIC_ISO_DATE)));
    }

    @Test
    void generatedIdsAreUniqueConcurrently() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(16);
        Set<Future<String>> futures = new HashSet<>();
        try {
            for (int index = 0; index < 1_000; index++) {
                futures.add(executor.submit(IdGenerator::nextTransactionId));
            }
            Set<String> generatedIds = new HashSet<>();
            for (Future<String> future : futures) {
                generatedIds.add(future.get(10, TimeUnit.SECONDS));
            }
            assertEquals(1_000, generatedIds.size());
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void separateGenerationBatchesDoNotDependOnPriorInMemoryState() {
        String firstBatchId = IdGenerator.nextTransactionId();
        String secondBatchId = IdGenerator.nextTransactionId();

        assertNotEquals(firstBatchId, secondBatchId);
        assertTrue(TRANSACTION_ID_PATTERN.matcher(secondBatchId).matches());
    }

    @Test
    void databasePrimaryKeyRejectsDuplicateTransactionId() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("id-constraint.db");
        DatabaseInitializer.initialize(databaseFile);
        try (Connection connection = DatabaseConnection.openConnection(databaseFile)) {
            seedAccounts(connection);
            TransactionRepository repository = new TransactionRepository(connection);
            Transaction transaction = transaction("TXN-DUPLICATE");
            repository.save(transaction);
            assertThrows(DataAccessException.class, () -> repository.save(transaction));
        }
    }

    @Test
    void existingPersistedTransactionIdRemainsReadable() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("existing-id.db");
        DatabaseInitializer.initialize(databaseFile);
        try (Connection connection = DatabaseConnection.openConnection(databaseFile)) {
            seedAccounts(connection);
            TransactionRepository repository = new TransactionRepository(connection);
            repository.save(transaction("TXN-20260925-000001"));

            assertEquals("TXN-20260925-000001",
                    repository.findById("TXN-20260925-000001").orElseThrow().getTransactionId());
        }
    }

    private Transaction transaction(String transactionId) {
        return new Transaction(transactionId, TransactionType.TRANSFER, "A001", "A002",
                new BigDecimal("1.00"), TransactionStatus.SUCCESS, "id test",
                LocalDateTime.of(2026, 9, 25, 10, 30));
    }

    private void seedAccounts(Connection connection) {
        LocalDateTime createdAt = LocalDateTime.of(2026, 9, 25, 10, 30);
        new BankRepository(connection).save(new Bank(1L, "BANK-A", "Test Bank", createdAt));
        AccountRepository repository = new AccountRepository(connection);
        repository.save(new Account("A001", "Account One", 1L, AccountType.SAVINGS,
                BigDecimal.ZERO, AccountStatus.ACTIVE, createdAt, createdAt));
        repository.save(new Account("A002", "Account Two", 1L, AccountType.SAVINGS,
                BigDecimal.ZERO, AccountStatus.ACTIVE, createdAt, createdAt));
    }
}