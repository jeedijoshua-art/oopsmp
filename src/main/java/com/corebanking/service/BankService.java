package com.corebanking.service;

import com.corebanking.model.Bank;

import java.util.List;

public interface BankService {
    Bank createBank(String bankCode, String bankName);

    List<Bank> listBanks();
}
