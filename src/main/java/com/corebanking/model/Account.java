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
    private final Integer age;
    private final String email;
    private final String phoneNumber;
    private final Long bankId;
    private final AccountType accountType;
    private final BigDecimal balance;
    private final AccountStatus status;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public Account(String accountId, String customerName, Integer age, String email, String phoneNumber, Long bankId, AccountType accountType,
                   BigDecimal balance, AccountStatus status, LocalDateTime createdAt,
                   LocalDateTime updatedAt) {
        this.accountId = requireText(accountId, "Account ID");
        this.customerName = requireText(customerName, "Customer name");
        this.age = age;
        this.email = email;
        this.phoneNumber = phoneNumber;
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

    public Account(String accountId, String customerName, Integer age, String email, String phoneNumber, Long bankId, AccountType accountType,
                   BigDecimal initialBalance, LocalDateTime createdAt) {
        this(accountId, customerName, age, email, phoneNumber, bankId, accountType, initialBalance, AccountStatus.ACTIVE,
                createdAt, createdAt);
    }
    
    // For backward compatibility / existing code
    public Account(String accountId, String customerName, Long bankId, AccountType accountType,
                   BigDecimal initialBalance, LocalDateTime createdAt) {
        this(accountId, customerName, null, null, null, bankId, accountType, initialBalance, AccountStatus.ACTIVE,
                createdAt, createdAt);
    }

    public String getAccountId() {
        return accountId;
    }

    public String getCustomerName() {
        return customerName;
    }

    public Integer getAge() {
        return age;
    }

    public String getEmail() {
        return email;
    }

    public String getPhoneNumber() {
        return phoneNumber;
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
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Account account)) {
            return false;
        }
        return accountId.equalsIgnoreCase(account.accountId);
    }

    @Override
    public int hashCode() {
        return accountId.toLowerCase().hashCode();
    }

    @Override
    public String toString() {
        return "Account{" +
                "accountId='" + accountId + '\'' +
                ", customerName='" + customerName + '\'' +
                ", age=" + age +
                ", email='" + email + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
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
