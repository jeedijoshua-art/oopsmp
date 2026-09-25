package com.corebanking.model;

import com.corebanking.enums.LedgerEntryType;
import com.corebanking.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class LedgerEntry {
    private final Long ledgerEntryId;
    private final String transactionId;
    private final String accountId;
    private final LedgerEntryType entryType;
    private final BigDecimal amount;
    private final String description;
    private final LocalDateTime createdAt;

    public LedgerEntry(Long ledgerEntryId, String transactionId, String accountId,
                       LedgerEntryType entryType, BigDecimal amount, String description,
                       LocalDateTime createdAt) {
        if (ledgerEntryId == null || ledgerEntryId <= 0) {
            throw new IllegalArgumentException("Ledger entry ID must be positive.");
        }
        this.ledgerEntryId = ledgerEntryId;
        this.transactionId = requireText(transactionId, "Transaction ID");
        this.accountId = requireText(accountId, "Account ID");
        this.entryType = Objects.requireNonNull(entryType, "Ledger entry type is required.");
        this.amount = MoneyUtil.requirePositive(amount);
        this.description = description;
        this.createdAt = Objects.requireNonNull(createdAt, "Created time is required.");
    }

    public Long getLedgerEntryId() {
        return ledgerEntryId;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public String getAccountId() {
        return accountId;
    }

    public LedgerEntryType getEntryType() {
        return entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "LedgerEntry{" +
                "ledgerEntryId=" + ledgerEntryId +
                ", transactionId='" + transactionId + '\'' +
                ", accountId='" + accountId + '\'' +
                ", entryType=" + entryType +
                ", amount=" + amount +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }
}
