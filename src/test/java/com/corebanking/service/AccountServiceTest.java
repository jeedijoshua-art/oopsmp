package com.corebanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.exception.AccountClosureException;
import com.corebanking.exception.AccountNotFoundException;
import com.corebanking.exception.DuplicateAccountException;
import com.corebanking.exception.InvalidAccountDataException;
import com.corebanking.exception.InvalidAccountStateException;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AccountServiceTest {
    private static final LocalDateTime CREATED_AT = LocalDateTime.of(2026, 9, 25, 10, 30);

    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private AccountService accountService;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("account-service.db");
        DatabaseInitializer.initialize(databaseFile);
        connection = DatabaseConnection.openConnection(databaseFile);
        new BankRepository(connection).save(new Bank(1L, "BANK-A", "Test Bank", CREATED_AT));
        accountService = new AccountServiceImpl(new AccountRepository(connection), new BankRepository(connection));
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void createsValidAccountAsActive() {
        Account account = accountService.createAccount("ACC100001", "Alice", 1L,
                AccountType.SAVINGS, new BigDecimal("125.50"));

        assertEquals("ACC100001", account.getAccountId());
        assertEquals(AccountStatus.ACTIVE, account.getStatus());
        assertEquals(new BigDecimal("125.50"), account.getBalance());
        assertTrue(accountService.accountExists("ACC100001"));
    }

    @Test
    void rejectsDuplicateAccount() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS, BigDecimal.ZERO);

        assertThrows(DuplicateAccountException.class,
                () -> accountService.createAccount("ACC100001", "Bob", 1L,
                        AccountType.CURRENT, BigDecimal.ZERO));
    }

    @Test
    void rejectsInvalidCustomerName() {
        assertThrows(InvalidAccountDataException.class,
                () -> accountService.createAccount("ACC100001", " ", 1L,
                        AccountType.SAVINGS, BigDecimal.ZERO));
    }

    @Test
    void rejectsInvalidBank() {
        assertThrows(InvalidAccountDataException.class,
                () -> accountService.createAccount("ACC100001", "Alice", 99L,
                        AccountType.SAVINGS, BigDecimal.ZERO));
    }

    @Test
    void rejectsNegativeInitialBalance() {
        assertThrows(InvalidAccountDataException.class,
                () -> accountService.createAccount("ACC100001", "Alice", 1L,
                        AccountType.SAVINGS, new BigDecimal("-0.01")));
    }

    @Test
    void rejectsInvalidAccountId() {
        assertThrows(InvalidAccountDataException.class,
                () -> accountService.createAccount("ACC 001", "Alice", 1L,
                        AccountType.SAVINGS, BigDecimal.ZERO));
    }

    @Test
    void findsListsAndSearchesAccounts() {
        accountService.createAccount("ACC100001", "Alice Johnson", 1L, AccountType.SAVINGS, BigDecimal.ZERO);
        accountService.createAccount("ACC100002", "Bob Smith", 1L, AccountType.CURRENT, BigDecimal.ZERO);

        assertEquals("Alice Johnson", accountService.findAccount("ACC100001").getCustomerName());
        assertEquals(2, accountService.listAccounts().size());
        assertEquals(1, accountService.searchAccounts("alice").size());
        assertEquals("ACC100002", accountService.searchAccounts("100002").get(0).getAccountId());
        assertThrows(AccountNotFoundException.class, () -> accountService.findAccount("missing"));
    }

    @Test
    void updatesCustomerNameAndTypeWithoutChangingBalance() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS,
                new BigDecimal("125.50"));

        Account updated = accountService.updateAccount("ACC100001", "Alice Updated", AccountType.CURRENT);

        assertEquals("Alice Updated", updated.getCustomerName());
        assertEquals(AccountType.CURRENT, updated.getAccountType());
        assertEquals(new BigDecimal("125.50"), accountService.getBalance("ACC100001"));
    }

    @Test
    void activatesDeactivatesAndReactivatesAccount() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS, BigDecimal.ZERO);

        assertEquals(AccountStatus.INACTIVE, accountService.deactivateAccount("ACC100001").getStatus());
        assertEquals(AccountStatus.ACTIVE, accountService.activateAccount("ACC100001").getStatus());
    }

    @Test
    void closesZeroBalanceAccount() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS, BigDecimal.ZERO);

        assertEquals(AccountStatus.CLOSED, accountService.closeAccount("ACC100001").getStatus());
    }

    @Test
    void rejectsClosingNonZeroBalanceAccount() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS,
                new BigDecimal("1.00"));

        assertThrows(AccountClosureException.class, () -> accountService.closeAccount("ACC100001"));
    }

    @Test
    void rejectsActivationAndDeactivationOfClosedAccount() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS, BigDecimal.ZERO);
        accountService.closeAccount("ACC100001");

        assertThrows(InvalidAccountStateException.class, () -> accountService.activateAccount("ACC100001"));
        assertThrows(InvalidAccountStateException.class, () -> accountService.deactivateAccount("ACC100001"));
    }

    @Test
    void rejectsMissingAccountForLifecycleAndBalanceOperations() {
        assertThrows(AccountNotFoundException.class, () -> accountService.deactivateAccount("missing"));
        assertThrows(AccountNotFoundException.class, () -> accountService.closeAccount("missing"));
        assertThrows(AccountNotFoundException.class, () -> accountService.getBalance("missing"));
    }

    @Test
    void validatesTransactionEligibilityByLifecycleState() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS, BigDecimal.ZERO);
        accountService.validateAccountCanTransact("ACC100001");

        accountService.deactivateAccount("ACC100001");
        assertThrows(InvalidAccountStateException.class,
                () -> accountService.validateAccountCanTransact("ACC100001"));

        accountService.activateAccount("ACC100001");
        accountService.closeAccount("ACC100001");
        assertThrows(InvalidAccountStateException.class,
                () -> accountService.validateAccountCanTransact("ACC100001"));
    }

    @Test
    void returnsExactBigDecimalBalance() {
        accountService.createAccount("ACC100001", "Alice", 1L, AccountType.SAVINGS,
                new BigDecimal("12345678901234567890.12"));

        assertEquals(new BigDecimal("12345678901234567890.12"),
                accountService.getBalance("ACC100001"));
        assertFalse(accountService.accountExists("missing"));
    }
}
