package com.corebanking.service;

import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;
import com.corebanking.repository.LedgerRepository;
import com.corebanking.repository.TransactionRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class LedgerServiceImpl implements LedgerService {
    public static final String SYSTEM_CASH_ACCOUNT_ID = "SYSTEM-CASH";

    private final LedgerRepository ledgerRepository;
    private final TransactionRepository transactionRepository;

    public LedgerServiceImpl(LedgerRepository ledgerRepository, TransactionRepository transactionRepository) {
        this.ledgerRepository = Objects.requireNonNull(ledgerRepository, "Ledger repository is required.");
        this.transactionRepository = Objects.requireNonNull(transactionRepository,
                "Transaction repository is required.");
    }

    @Override
    public List<LedgerEntry> createDepositEntries(Transaction transaction) {
        requireType(transaction, TransactionType.DEPOSIT);
        return createPair(transaction, SYSTEM_CASH_ACCOUNT_ID, transaction.getToAccount());
    }

    @Override
    public List<LedgerEntry> createWithdrawalEntries(Transaction transaction) {
        requireType(transaction, TransactionType.WITHDRAWAL);
        return createPair(transaction, transaction.getFromAccount(), SYSTEM_CASH_ACCOUNT_ID);
    }

    @Override
    public List<LedgerEntry> createTransferEntries(Transaction transaction) {
        requireType(transaction, TransactionType.TRANSFER);
        return createPair(transaction, transaction.getFromAccount(), transaction.getToAccount());
    }

    @Override
    public void saveEntries(List<LedgerEntry> entries) {
        validateBalancedEntries(entries);
        ledgerRepository.saveAll(entries);
    }

    @Override
    public void validateBalancedEntries(List<LedgerEntry> entries) {
        BigDecimal debits = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credits = entries.stream()
                .filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (debits.compareTo(credits) != 0) {
            throw new DataAccessException("Ledger entries are not balanced.", null);
        }
    }

    @Override
    public void validateBalancedTransaction(String transactionId) {
        if (transactionRepository.findById(transactionId).isEmpty()) {
            throw new DataAccessException("Transaction not found: " + transactionId, null);
        }
        List<LedgerEntry> entries = ledgerRepository.findByTransactionId(transactionId);
        if (entries.isEmpty()) {
            throw new DataAccessException("No ledger entries found for transaction: " + transactionId, null);
        }
        validateBalancedEntries(entries);
    }

    @Override
    public BigDecimal totalDebits(String transactionId) {
        return total(transactionId, LedgerEntryType.DEBIT);
    }

    @Override
    public BigDecimal totalCredits(String transactionId) {
        return total(transactionId, LedgerEntryType.CREDIT);
    }

    private BigDecimal total(String transactionId, LedgerEntryType type) {
        return ledgerRepository.findByTransactionId(transactionId).stream()
                .filter(entry -> entry.getEntryType() == type)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private LedgerEntry entry(Long id, Transaction transaction, String accountId, LedgerEntryType type) {
        if (accountId == null || accountId.isBlank()) {
            throw new DataAccessException("Ledger account is required for transaction: "
                    + transaction.getTransactionId(), null);
        }
        return new LedgerEntry(id, transaction.getTransactionId(), accountId, type,
                transaction.getAmount(), transaction.getDescription(), LocalDateTime.now());
    }

    private static void requireType(Transaction transaction, TransactionType expected) {
        if (transaction == null || transaction.getTransactionType() != expected) {
            throw new IllegalArgumentException("Expected a " + expected + " transaction.");
        }
    }

    private List<LedgerEntry> createPair(Transaction transaction, String debitAccountId, String creditAccountId) {
        long debitId = ledgerRepository.nextLedgerEntryId();
        return List.of(entry(debitId, transaction, debitAccountId, LedgerEntryType.DEBIT),
                entry(debitId + 1, transaction, creditAccountId, LedgerEntryType.CREDIT));
    }
}