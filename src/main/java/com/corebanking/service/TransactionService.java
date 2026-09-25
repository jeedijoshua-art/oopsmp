package com.corebanking.service;

import com.corebanking.model.Transaction;

import java.math.BigDecimal;

public interface TransactionService {
    Transaction deposit(String accountId, BigDecimal amount, String description);

    Transaction withdraw(String accountId, BigDecimal amount, String description);

    Transaction transfer(String fromAccountId, String toAccountId, BigDecimal amount, String description);
}
