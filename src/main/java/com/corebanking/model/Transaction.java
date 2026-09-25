package com.corebanking.model;

import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Transaction {
    private final String transactionId;
    private final TransactionType transactionType;
    private final String fromAccount;
    private final String toAccount;
    private final BigDecimal amount;
    private final TransactionStatus status;
    private final String description;
    private final LocalDateTime createdAt;

    public Transaction(String transactionId, TransactionType transactionType, String fromAccount,
                       String toAccount, BigDecimal amount, TransactionStatus status,
                       String description, LocalDateTime createdAt) {
        this.transactionId = requireText(transactionId, "Transaction ID");
        this.transactionType = Objects.requireNonNull(transactionType, "Transaction type is required.");
        this.fromAccount = optionalText(fromAccount, "From account");
        this.toAccount = optionalText(toAccount, "To account");
        this.amount = MoneyUtil.requirePositive(amount);
        this.status = Objects.requireNonNull(status, "Transaction status is required.");
        this.description = description;
        this.createdAt = Objects.requireNonNull(createdAt, "Created time is required.");
    }

    public String getTransactionId() {
        return transactionId;
    }

    public TransactionType getTransactionType() {
        return transactionType;
    }

    public String getFromAccount() {
        return fromAccount;
    }

    public String getToAccount() {
        return toAccount;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public String toString() {
        return "Transaction{" +
                "transactionId='" + transactionId + '\'' +
                ", transactionType=" + transactionType +
                ", fromAccount='" + fromAccount + '\'' +
                ", toAccount='" + toAccount + '\'' +
                ", amount=" + amount +
                ", status=" + status +
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

    private static String optionalText(String value, String fieldName) {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " cannot be blank.");
        }
        return value;
    }
}
