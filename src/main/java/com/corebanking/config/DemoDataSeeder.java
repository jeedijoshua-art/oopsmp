package com.corebanking.config;

import com.corebanking.enums.AccountType;
import com.corebanking.model.Bank;
import com.corebanking.model.Account;
import com.corebanking.service.AccountService;
import com.corebanking.service.BankService;
import com.corebanking.service.TransactionService;

import java.math.BigDecimal;
import java.util.List;

public final class DemoDataSeeder {
    private DemoDataSeeder() {}

    public static void seed(BankService bankService, AccountService accountService, TransactionService transactionService) {
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
    }
}
