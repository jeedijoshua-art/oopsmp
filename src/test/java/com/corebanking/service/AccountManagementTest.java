package com.corebanking.service;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.config.DemoDataSeeder;
import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.exception.DataAccessException;
import com.corebanking.exception.InvalidAccountStateException;
import com.corebanking.model.Account;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.TransactionRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;

import static org.junit.jupiter.api.Assertions.*;

public class AccountManagementTest {
    private Path tempDbFile;
    private Connection connection;
    private AccountService accountService;
    private BankService bankService;
    private TransactionService transactionService;
    private DatabaseManagementService dbmService;

    @BeforeEach
    void setUp() throws Exception {
        tempDbFile = Files.createTempFile("test_corebanking", ".db");
        DatabaseInitializer.initialize(tempDbFile);
        connection = DatabaseConnection.openConnection(tempDbFile);
        AccountRepository accountRepo = new AccountRepository(connection);
        BankRepository bankRepo = new BankRepository(connection);
        accountService = new AccountServiceImpl(accountRepo, bankRepo);
        bankService = new BankServiceImpl(bankRepo);
        transactionService = new TransactionServiceImpl(connection, accountService);
        dbmService = new DatabaseManagementServiceImpl(connection);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (connection != null && !connection.isClosed()) {
            connection.close();
        }
        Files.deleteIfExists(tempDbFile);
    }

    @Test
    void testCloseEligibleAccount() {
        bankService.createBank("TEST", "Test Bank");
        Long bankId = bankService.listBanks().get(0).getBankId();
        accountService.createAccount("ACC-1", "Test User", bankId, AccountType.SAVINGS, BigDecimal.ZERO);
        
        Account account = accountService.closeAccount("ACC-1");
        assertEquals(AccountStatus.CLOSED, account.getStatus());
    }

    @Test
    void testRejectTransactionOnClosedAccount() {
        bankService.createBank("TEST", "Test Bank");
        Long bankId = bankService.listBanks().get(0).getBankId();
        accountService.createAccount("ACC-1", "Test User", bankId, AccountType.SAVINGS, BigDecimal.ZERO);
        accountService.closeAccount("ACC-1");
        
        assertThrows(InvalidAccountStateException.class, () -> 
            transactionService.deposit("ACC-1", new BigDecimal("100.00"), "Deposit"));
    }

    @Test
    void testRejectDeletionWithHistory() {
        bankService.createBank("TEST", "Test Bank");
        Long bankId = bankService.listBanks().get(0).getBankId();
        accountService.createAccount("ACC-1", "Test User", bankId, AccountType.SAVINGS, BigDecimal.ZERO);
        transactionService.deposit("ACC-1", new BigDecimal("100.00"), "Deposit");
        transactionService.withdraw("ACC-1", new BigDecimal("100.00"), "Withdraw");
        accountService.closeAccount("ACC-1");

        assertThrows(DataAccessException.class, () -> accountService.deleteAccount("ACC-1"));
    }

    @Test
    void testAllowDeletionOfUnusedAccount() {
        bankService.createBank("TEST", "Test Bank");
        Long bankId = bankService.listBanks().get(0).getBankId();
        accountService.createAccount("ACC-1", "Test User", bankId, AccountType.SAVINGS, BigDecimal.ZERO);
        accountService.closeAccount("ACC-1");
        accountService.deleteAccount("ACC-1");

        assertFalse(accountService.accountExists("ACC-1"));
    }

    @Test
    void testDemoResetRemovesOnlyDemoRecords() {
        DemoDataSeeder.seed(connection, bankService, accountService, transactionService);
        
        bankService.createBank("USER_BANK", "User Bank");
        Long bankId = bankService.listBanks().stream().filter(b -> b.getBankCode().equals("USER_BANK")).findFirst().get().getBankId();
        accountService.createAccount("USER-ACC", "User", bankId, AccountType.SAVINGS, BigDecimal.ZERO);
        
        dbmService.resetDemoData();
        
        assertFalse(accountService.accountExists("ACC-JOSHUA"));
        assertTrue(accountService.accountExists("USER-ACC"));
    }

    @Test
    void testFullResetPreservesSchemaAndSystemAccounts() {
        DemoDataSeeder.seed(connection, bankService, accountService, transactionService);
        dbmService.resetEntireDatabase();
        
        assertTrue(accountService.listAccounts().isEmpty());
        assertTrue(bankService.listBanks().isEmpty());
    }
}
