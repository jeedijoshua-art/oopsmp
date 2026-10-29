package com.corebanking.model;

import java.math.BigDecimal;

public record AccountReconciliationRecord(
        String accountId,
        String customerName,
        String bankName,
        BigDecimal initialBalance,
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        BigDecimal ledgerDerivedBalance,
        BigDecimal actualBalance,
        BigDecimal discrepancy,
        boolean isReconciled
) {
    public boolean hasDiscrepancy() {
        return !isReconciled;
    }
}

