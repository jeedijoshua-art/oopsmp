package com.corebanking.service;

import com.corebanking.config.DatabaseTransaction;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.exception.InsufficientFundsException;
import com.corebanking.exception.InvalidAmountException;
import com.corebanking.exception.InvalidTransferException;
import com.corebanking.exception.TransactionFailedException;
import com.corebanking.model.Account;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.LedgerRepository;
import com.corebanking.repository.TransactionRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.transaction.AccountLockManager;
import com.corebanking.util.IdGenerator;
import com.corebanking.util.MoneyUtil;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.Objects;

public final class TransactionServiceImpl implements TransactionService {
    private final Connection connection;
    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final BankRepository bankRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerService ledgerService;
    private final AccountLockManager lockManager;

    public TransactionServiceImpl(Connection connection, AccountService accountService) {
        this(connection, accountService, new AccountRepository(connection), new TransactionRepository(connection),
            new LedgerServiceImpl(new LedgerRepository(connection), new TransactionRepository(connection)),
            new AccountLockManager());
    }

    public TransactionServiceImpl(Connection connection, AccountService accountService,
                                  AccountRepository accountRepository,
                                  TransactionRepository transactionRepository) {
        this(connection, accountService, accountRepository, transactionRepository,
                        new LedgerServiceImpl(new LedgerRepository(connection), transactionRepository),
                        new AccountLockManager());
        }

        public TransactionServiceImpl(Connection connection, AccountService accountService,
                      AccountRepository accountRepository,
                      TransactionRepository transactionRepository,
                          LedgerService ledgerService) {
                this(connection, accountService, accountRepository, transactionRepository, ledgerService,
                    new AccountLockManager());
                }

                public TransactionServiceImpl(Connection connection, AccountService accountService,
                          AccountRepository accountRepository,
                          TransactionRepository transactionRepository,
                          LedgerService ledgerService,
                          AccountLockManager lockManager) {
        this.connection = Objects.requireNonNull(connection, "Database connection is required.");
        this.accountService = Objects.requireNonNull(accountService, "Account service is required.");
        this.accountRepository = Objects.requireNonNull(accountRepository, "Account repository is required.");
        this.bankRepository = new BankRepository(connection);
        this.transactionRepository = Objects.requireNonNull(transactionRepository,
                "Transaction repository is required.");
        this.ledgerService = Objects.requireNonNull(ledgerService, "Ledger service is required.");
        this.lockManager = Objects.requireNonNull(lockManager, "Account lock manager is required.");
    }

    @Override
    public Transaction deposit(String accountId, BigDecimal amount, String description) {
        lockManager.lockAccount(accountId);
        try {
            BigDecimal validAmount = validateAmount(amount, "Deposit");
            Account account = validateActiveAccount(accountId);
            BigDecimal newBalance = account.getBalance().add(validAmount);
            Transaction transaction = new Transaction(IdGenerator.nextTransactionId(), TransactionType.DEPOSIT,
                LedgerServiceImpl.SYSTEM_CASH_ACCOUNT_ID, accountId, validAmount, TransactionStatus.SUCCESS,
                description, LocalDateTime.now());
            return executeAtomic(accountId, newBalance, transaction);
        } finally {
            lockManager.unlockAccount(accountId);
        }
    }

    @Override
    public Transaction withdraw(String accountId, BigDecimal amount, String description) {
        lockManager.lockAccount(accountId);
        try {
            BigDecimal validAmount = validateAmount(amount, "Withdrawal");
            Account account = validateActiveAccount(accountId);
            if (validAmount.compareTo(account.getBalance()) > 0) {
                throw new InsufficientFundsException(account.getBalance());
            }
            BigDecimal newBalance = account.getBalance().subtract(validAmount);
            Transaction transaction = new Transaction(IdGenerator.nextTransactionId(), TransactionType.WITHDRAWAL,
                accountId, LedgerServiceImpl.SYSTEM_CASH_ACCOUNT_ID, validAmount, TransactionStatus.SUCCESS,
                description, LocalDateTime.now());
            return executeAtomic(accountId, newBalance, transaction);
        } finally {
            lockManager.unlockAccount(accountId);
        }
    }

    @Override
    public Transaction transfer(String fromAccountId, String toAccountId, BigDecimal amount, String description) {
        requireTransferAccountId(fromAccountId, "Source account ID");
        requireTransferAccountId(toAccountId, "Destination account ID");
        if (fromAccountId.equals(toAccountId)) {
            throw new InvalidTransferException("Source and destination accounts must be different.");
        }

        try (AccountLockManager.LockScope ignored = lockManager.lockAccounts(fromAccountId, toAccountId)) {
            BigDecimal validAmount = validateAmount(amount, "Transfer");
            accountService.validateAccountCanTransact(fromAccountId);
            accountService.validateAccountCanTransact(toAccountId);
            Account source = accountService.findAccount(fromAccountId);
            Account destination = accountService.findAccount(toAccountId);
            if (validAmount.compareTo(source.getBalance()) > 0) {
            throw new InsufficientFundsException(source.getBalance());
            }

            Transaction transaction = new Transaction(IdGenerator.nextTransactionId(), TransactionType.TRANSFER,
                fromAccountId, toAccountId, validAmount, TransactionStatus.SUCCESS, description,
                LocalDateTime.now());
            return executeTransferAtomic(source.getBalance().subtract(validAmount),
                destination.getBalance().add(validAmount), transaction);
        }
    }

    private Transaction executeAtomic(String accountId, BigDecimal newBalance, Transaction transaction) {
        bankRepository.ensureSystemBank();
        accountRepository.ensureSystemCashAccount();
        try (DatabaseTransaction databaseTransaction = new DatabaseTransaction(connection)) {
            try {
                accountRepository.updateBalance(accountId, newBalance);
                transactionRepository.save(transaction);
                if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
                    ledgerService.saveEntries(ledgerService.createDepositEntries(transaction));
                } else {
                    ledgerService.saveEntries(ledgerService.createWithdrawalEntries(transaction));
                }
                ledgerService.validateBalancedTransaction(transaction.getTransactionId());
                databaseTransaction.commit();
                return transaction;
            } catch (RuntimeException exception) {
                rollback(databaseTransaction, exception);
                throw new TransactionFailedException(
                        "Failed to complete transaction: " + transaction.getTransactionId(), exception);
            } catch (SQLException exception) {
                rollback(databaseTransaction, exception);
                throw new TransactionFailedException(
                        "Failed to complete transaction: " + transaction.getTransactionId(), exception);
            }
        } catch (SQLException exception) {
            throw new TransactionFailedException(
                    "Failed to close transaction: " + transaction.getTransactionId(), exception);
        }
    }

    private Transaction executeTransferAtomic(BigDecimal sourceBalance, BigDecimal destinationBalance,
                                              Transaction transaction) {
        try (DatabaseTransaction databaseTransaction = new DatabaseTransaction(connection)) {
            try {
                accountRepository.updateBalance(transaction.getFromAccount(), sourceBalance);
                accountRepository.updateBalance(transaction.getToAccount(), destinationBalance);
                transactionRepository.save(transaction);
                ledgerService.saveEntries(ledgerService.createTransferEntries(transaction));
                ledgerService.validateBalancedTransaction(transaction.getTransactionId());
                databaseTransaction.commit();
                return transaction;
            } catch (RuntimeException exception) {
                rollback(databaseTransaction, exception);
                throw new TransactionFailedException(
                        "Failed to complete transfer: " + transaction.getTransactionId(), exception);
            } catch (SQLException exception) {
                rollback(databaseTransaction, exception);
                throw new TransactionFailedException(
                        "Failed to complete transfer: " + transaction.getTransactionId(), exception);
            }
        } catch (SQLException exception) {
            throw new TransactionFailedException(
                    "Failed to close transfer: " + transaction.getTransactionId(), exception);
        }
    }

    private Account validateActiveAccount(String accountId) {
        accountService.validateAccountCanTransact(accountId);
        return accountService.findAccount(accountId);
    }

    private static BigDecimal validateAmount(BigDecimal amount, String operation) {
        try {
            return MoneyUtil.requirePositive(amount);
        } catch (IllegalArgumentException exception) {
            throw new InvalidAmountException(operation + " amount must be greater than zero.", exception);
        }
    }

    private static void requireTransferAccountId(String accountId, String fieldName) {
        if (accountId == null || accountId.isBlank()) {
            throw new InvalidTransferException(fieldName + " is required.");
        }
    }

    private static void rollback(DatabaseTransaction databaseTransaction, Exception failure) {
        try {
            databaseTransaction.rollback();
        } catch (SQLException rollbackFailure) {
            failure.addSuppressed(rollbackFailure);
        }
    }
}
