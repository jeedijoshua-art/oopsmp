package com.corebanking.enums;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

import org.junit.jupiter.api.Test;

class EnumTest {
    @Test
    void enumValuesAreCorrect() {
        assertArrayEquals(new AccountType[]{AccountType.SAVINGS, AccountType.CURRENT}, AccountType.values());
        assertArrayEquals(new AccountStatus[]{AccountStatus.ACTIVE, AccountStatus.INACTIVE, AccountStatus.CLOSED},
                AccountStatus.values());
        assertArrayEquals(new TransactionType[]{TransactionType.DEPOSIT, TransactionType.WITHDRAWAL,
                TransactionType.TRANSFER}, TransactionType.values());
        assertArrayEquals(new TransactionStatus[]{TransactionStatus.PENDING, TransactionStatus.SUCCESS,
                TransactionStatus.FAILED, TransactionStatus.ROLLED_BACK}, TransactionStatus.values());
        assertArrayEquals(new LedgerEntryType[]{LedgerEntryType.DEBIT, LedgerEntryType.CREDIT},
                LedgerEntryType.values());
    }
}
