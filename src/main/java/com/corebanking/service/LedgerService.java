package com.corebanking.service;

import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;

import java.math.BigDecimal;
import java.util.List;

public interface LedgerService {
    List<LedgerEntry> createDepositEntries(Transaction transaction);

    List<LedgerEntry> createWithdrawalEntries(Transaction transaction);

    List<LedgerEntry> createTransferEntries(Transaction transaction);

    void saveEntries(List<LedgerEntry> entries);

    void validateBalancedEntries(List<LedgerEntry> entries);

    void validateBalancedTransaction(String transactionId);

    BigDecimal totalDebits(String transactionId);

    BigDecimal totalCredits(String transactionId);
}