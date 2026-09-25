package com.corebanking.exception;

import java.math.BigDecimal;

public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(BigDecimal availableBalance) {
        super("Insufficient funds. Available balance: " + availableBalance.toPlainString());
    }
}
