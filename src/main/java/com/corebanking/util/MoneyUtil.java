package com.corebanking.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class MoneyUtil {
    public static final int SCALE = 2;

    private MoneyUtil() {
    }

    public static BigDecimal normalize(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount is required.");
        }
        if (amount.scale() > SCALE) {
            throw new IllegalArgumentException("Amount must have at most two decimal places.");
        }
        return amount.setScale(SCALE, RoundingMode.UNNECESSARY);
    }

    public static BigDecimal requireNonNegative(BigDecimal amount) {
        BigDecimal normalizedAmount = normalize(amount);
        if (normalizedAmount.signum() < 0) {
            throw new IllegalArgumentException("Amount cannot be negative.");
        }
        return normalizedAmount;
    }

    public static BigDecimal requirePositive(BigDecimal amount) {
        BigDecimal normalizedAmount = normalize(amount);
        if (normalizedAmount.signum() <= 0) {
            throw new IllegalArgumentException("Amount must be positive.");
        }
        return normalizedAmount;
    }
}
