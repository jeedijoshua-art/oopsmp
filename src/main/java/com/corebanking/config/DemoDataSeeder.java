package com.corebanking.config;

import com.corebanking.enums.AccountType;
import com.corebanking.model.Bank;
import com.corebanking.model.Account;
import com.corebanking.service.AccountService;
import com.corebanking.service.BankService;
import com.corebanking.service.TransactionService;

import java.math.BigDecimal;
import java.util.List;

import java.sql.Connection;
import java.sql.Statement;

public final class DemoDataSeeder {
    private DemoDataSeeder() {}

    public static void seed(Connection connection, BankService bankService, AccountService accountService, TransactionService transactionService) {
        List<Bank> banks = bankService.listBanks();
        if (banks.stream().anyMatch(b -> b.getBankCode().equals("NSB"))) {
            // Already seeded or exists
            return;
        }

        // Create Bank
        Bank bank = bankService.createBank("NSB", "Northstar Bank");

        // Create Accounts
        Account joshua = accountService.createAccount("ACC-JOSHUA", "Joshua", bank.getBankId(), AccountType.SAVINGS, new BigDecimal("1000.00"));
        Account roshan = accountService.createAccount("ACC-ROSHAN", "Roshan", bank.getBankId(), AccountType.CURRENT, new BigDecimal("500.00"));
        Account alice = accountService.createAccount("ACC-ALICE", "Alice", bank.getBankId(), AccountType.SAVINGS, new BigDecimal("250.00"));

        // Transactions
        transactionService.deposit(joshua.getAccountId(), new BigDecimal("500.00"), "Initial Deposit");
        transactionService.deposit(roshan.getAccountId(), new BigDecimal("200.00"), "Initial Deposit");
        transactionService.withdraw(joshua.getAccountId(), new BigDecimal("100.00"), "ATM Withdrawal");
        transactionService.transfer(joshua.getAccountId(), roshan.getAccountId(), new BigDecimal("150.00"), "Rent payment");
        transactionService.transfer(roshan.getAccountId(), alice.getAccountId(), new BigDecimal("50.00"), "Dinner");

        try (Statement statement = connection.createStatement()) {
            statement.execute("UPDATE banks SET is_demo = 1 WHERE bank_code = 'NSB'");
            statement.execute("UPDATE accounts SET is_demo = 1 WHERE bank_id = " + bank.getBankId());
            statement.execute("UPDATE transactions SET is_demo = 1 WHERE from_account IN ('ACC-JOSHUA', 'ACC-ROSHAN', 'ACC-ALICE') OR to_account IN ('ACC-JOSHUA', 'ACC-ROSHAN', 'ACC-ALICE')");
            statement.execute("UPDATE ledger_entries SET is_demo = 1 WHERE account_id IN ('ACC-JOSHUA', 'ACC-ROSHAN', 'ACC-ALICE') OR account_id = 'SYSTEM-CASH'");
        } catch (Exception e) {
            System.err.println("Failed to mark demo data: " + e.getMessage());
        }
    }
}
