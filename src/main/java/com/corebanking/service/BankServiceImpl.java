package com.corebanking.service;

import com.corebanking.exception.InvalidAccountDataException;
import com.corebanking.model.Bank;
import com.corebanking.repository.BankRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

public final class BankServiceImpl implements BankService {
    private final BankRepository bankRepository;

    public BankServiceImpl(BankRepository bankRepository) {
        this.bankRepository = Objects.requireNonNull(bankRepository, "Bank repository is required.");
    }

    @Override
    public Bank createBank(String bankCode, String bankName) {
        if (bankCode == null || bankCode.isBlank() || bankCode.equalsIgnoreCase("SYSTEM")) {
            throw new InvalidAccountDataException("Enter a valid bank code.");
        }
        if (bankName == null || bankName.isBlank()) {
            throw new InvalidAccountDataException("Enter a bank name.");
        }
        if (bankRepository.findByCode(bankCode.trim()).isPresent()) {
            throw new InvalidAccountDataException("A bank with this code already exists.");
        }
        long nextId = bankRepository.findAll().stream()
                .mapToLong(Bank::getBankId)
                .max()
                .orElse(0L) + 1;
        Bank bank = new Bank(nextId, bankCode.trim(), bankName.trim(), LocalDateTime.now());
        bankRepository.save(bank);
        return bank;
    }

    @Override
    public List<Bank> listBanks() {
        return bankRepository.findAll();
    }
}
