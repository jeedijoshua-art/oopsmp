package com.corebanking.service;

import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.model.Account;

import java.math.BigDecimal;
import java.util.List;

public interface AccountService {
    Account createAccount(String accountId, String customerName, Long bankId,
                          AccountType accountType, BigDecimal initialBalance);

    Account findAccount(String accountId);

    List<Account> listAccounts();

    List<Account> searchAccounts(String searchTerm);

    Account updateAccount(String accountId, String customerName, AccountType accountType);

    Account activateAccount(String accountId);

    Account deactivateAccount(String accountId);

    Account closeAccount(String accountId);

    boolean accountExists(String accountId);

    BigDecimal getBalance(String accountId);

    void validateAccountCanTransact(String accountId);
}
