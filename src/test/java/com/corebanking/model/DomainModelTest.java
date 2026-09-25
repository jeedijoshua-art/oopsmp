package com.corebanking.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

class DomainModelTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 10, 30);

    @Test
    void validBankIsCreated() {
        Bank bank = new Bank(1L, "BANK-A", "Academic Bank", CREATED_AT);

        assertEquals(1L, bank.getBankId());
        assertEquals("BANK-A", bank.getBankCode());
        assertEquals("Academic Bank", bank.getBankName());
        assertEquals(CREATED_AT, bank.getCreatedAt());
    }

    @Test
    void invalidBankDataIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Bank(0L, "BANK-A", "Academic Bank", CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new Bank(1L, " ", "Academic Bank", CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new Bank(1L, "BANK-A", " ", CREATED_AT));
    }

    @Test
    void validAccountUsesNormalizedBalanceAndActiveStatus() {
        Account account = new Account("A001", "Test Customer", 1L, AccountType.SAVINGS,
                new BigDecimal("100"), CREATED_AT);

        assertEquals(new BigDecimal("100.00"), account.getBalance());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(CREATED_AT, account.getUpdatedAt());
    }

    @Test
    void negativeAccountBalanceIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account("A001", "Test Customer", 1L, AccountType.SAVINGS,
                        new BigDecimal("-0.01"), CREATED_AT));
    }

    @Test
    void blankAccountIdIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account(" ", "Test Customer", 1L, AccountType.SAVINGS,
                        BigDecimal.ZERO, CREATED_AT));
    }

    @Test
    void blankCustomerNameIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Account("A001", " ", 1L, AccountType.SAVINGS,
                        BigDecimal.ZERO, CREATED_AT));
    }

    @Test
    void validTransactionIsCreated() {
        Transaction transaction = new Transaction("TXN-001", TransactionType.TRANSFER,
                "A001", "A002", new BigDecimal("5000.00"), TransactionStatus.PENDING,
                "Test transfer", CREATED_AT);

        assertEquals("TXN-001", transaction.getTransactionId());
        assertEquals(new BigDecimal("5000.00"), transaction.getAmount());
        assertEquals(TransactionStatus.PENDING, transaction.getStatus());
    }

    @Test
    void zeroTransactionAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("TXN-001", TransactionType.DEPOSIT, null, "A001",
                        BigDecimal.ZERO, TransactionStatus.PENDING, null, CREATED_AT));
    }

    @Test
    void negativeTransactionAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new Transaction("TXN-001", TransactionType.WITHDRAWAL, "A001", null,
                        new BigDecimal("-1.00"), TransactionStatus.PENDING, null, CREATED_AT));
    }

    @Test
    void validLedgerEntryIsCreated() {
        LedgerEntry entry = new LedgerEntry(1L, "TXN-001", "A001", LedgerEntryType.DEBIT,
                new BigDecimal("5000.00"), "Debit entry", CREATED_AT);

        assertEquals("TXN-001", entry.getTransactionId());
        assertEquals(LedgerEntryType.DEBIT, entry.getEntryType());
        assertEquals(new BigDecimal("5000.00"), entry.getAmount());
    }

    @Test
    void invalidLedgerEntryAmountIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new LedgerEntry(1L, "TXN-001", "A001", LedgerEntryType.DEBIT,
                        BigDecimal.ZERO, null, CREATED_AT));
        assertThrows(IllegalArgumentException.class,
                () -> new LedgerEntry(1L, "TXN-001", "A001", LedgerEntryType.DEBIT,
                        new BigDecimal("1.001"), null, CREATED_AT));
    }

    @Test
    void ledgerEntryFieldsAreFinalAndThereAreNoSetters() {
        assertEquals(0, Arrays.stream(LedgerEntry.class.getDeclaredMethods())
                .filter(method -> method.getName().startsWith("set"))
                .count());
        for (Field field : LedgerEntry.class.getDeclaredFields()) {
            if (!field.isSynthetic()) {
                assertEquals(true, Modifier.isFinal(field.getModifiers()));
            }
        }
    }
}
