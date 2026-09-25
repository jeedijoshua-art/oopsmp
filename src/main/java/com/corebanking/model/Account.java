package com.corebanking.model;

import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.util.MoneyUtil;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;

public final class Account {
    private final String accountId;
    private final String customerName;
    private final Long bankId;
    private final AccountType accountType;
    private final BigDecimal balance;
    private final AccountStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Account(String accountId, String customerName, Long bankId, AccountType accountType,
                   BigDecimal balance, AccountStatus status, LocalDateTime createdAt,
                   LocalDateTime updatedAt) {
        this.accountId = requireText(accountId, "Account ID");
        this.customerName = requireText(customerName, "Customer name");
        if (bankId == null || bankId <= 0) {
            throw new IllegalArgumentException("Bank ID must be positive.");
        }
        this.bankId = bankId;
        this.accountType = Objects.requireNonNull(accountType, "Account type is required.");
        this.balance = MoneyUtil.requireNonNegative(balance);
        this.status = Objects.requireNonNull(status, "Account status is required.");
        this.createdAt = Objects.requireNonNull(createdAt, "Created time is required.");
        this.updatedAt = Objects.requireNonNull(updatedAt, "Updated time is required.");
    }

    public Account(String accountId, String customerName, Long bankId, AccountType accountType,
                   BigDecimal initialBalance, LocalDateTime createdAt) {
        this(accountId, customerName, bankId, accountType, initialBalance, AccountStatus.ACTIVE,
                createdAt, createdAt);
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Long getBankId() {
        return bankId;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AccountStatus getStatus() {
        return status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", customerName='" + customerName + '\'' +
                ", bankId=" + bankId +
                ", accountType=" + accountType +
                ", balance=" + balance +
                ", status=" + status +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                '}';
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " is required.");
        }
        return value;
    }
}
