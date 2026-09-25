package com.corebanking.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.corebanking.config.DatabaseConnection;
import com.corebanking.config.DatabaseInitializer;
import com.corebanking.exception.InvalidAccountDataException;
import com.corebanking.repository.BankRepository;

import java.nio.file.Path;
import java.sql.Connection;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BankServiceTest {
    @TempDir
    Path temporaryDirectory;

    private Connection connection;
    private BankService bankService;

    @BeforeEach
    void setUp() throws Exception {
        Path databaseFile = temporaryDirectory.resolve("bank-service.db");
        DatabaseInitializer.initialize(databaseFile);
        connection = DatabaseConnection.openConnection(databaseFile);
        bankService = new BankServiceImpl(new BankRepository(connection));
    }

    @AfterEach
    void tearDown() throws Exception {
        connection.close();
    }

    @Test
    void createsBankForFirstRunAccountSetup() {
        var bank = bankService.createBank("BANK-A", "Academic Bank");

        assertEquals("BANK-A", bank.getBankCode());
        assertEquals(1, bankService.listBanks().size());
    }

    @Test
    void rejectsDuplicateBankCode() {
        bankService.createBank("BANK-A", "Academic Bank");

        assertThrows(InvalidAccountDataException.class,
                () -> bankService.createBank("BANK-A", "Another Bank"));
    }
}
