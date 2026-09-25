package com.corebanking.model;

import java.time.LocalDateTime;
import java.util.Objects;

public final class Bank {
    private final Long bankId;
    private final String bankCode;
    private final String bankName;
    private final LocalDateTime createdAt;

    public Bank(Long bankId, String bankCode, String bankName, LocalDateTime createdAt) {
        if (bankId == null || bankId <= 0) {
            throw new IllegalArgumentException("Bank ID must be positive.");
        }
        this.bankId = bankId;
        this.bankCode = requireText(bankCode, "Bank code");
        this.bankName = requireText(bankName, "Bank name");
        this.createdAt = Objects.requireNonNull(createdAt, "Created time is required.");
    }

    public Long getBankId() {
        return bankId;
    }

    public String getBankCode() {
        return bankCode;
    }

    public String getBankName() {
        return bankName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof Bank bank)) {
            return false;
        }
        return bankId.equals(bank.bankId);
    }

    @Override
    public int hashCode() {
        return bankId.hashCode();
    }

    @Override
    public String toString() {
        return "Bank{" +
                "bankId=" + bankId +
                ", bankCode='" + bankCode + '\'' +
                ", bankName='" + bankName + '\'' +
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
