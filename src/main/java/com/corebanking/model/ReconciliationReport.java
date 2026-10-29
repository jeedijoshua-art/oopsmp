package com.corebanking.model;

import java.math.BigDecimal;
import java.util.List;

public record ReconciliationReport(
        BigDecimal totalDebits,
        BigDecimal totalCredits,
        boolean isJournalBalanced,
        List<String> unbalancedTransactions,
        List<AccountReconciliationRecord> accountReconciliations,
        boolean isFullyReconciled,
        int discrepancyCount
) {
    public boolean hasDiscrepancies() {
        return discrepancyCount > 0 || !isJournalBalanced;
    }
}

