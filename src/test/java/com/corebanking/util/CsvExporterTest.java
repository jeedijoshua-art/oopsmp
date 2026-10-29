package com.corebanking.util;

import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CsvExporterTest {

    private static final LocalDateTime TEST_TIME = LocalDateTime.of(2026, 10, 29, 14, 30, 45);

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("Transaction CSV header matches required specification exactly")
    void testTransactionHeader() {
        assertEquals("Transaction ID,Type,From Account,To Account,Amount,Status,Timestamp,Description",
                CsvExporter.TRANSACTION_HEADER);
    }

    @Test
    @DisplayName("Ledger CSV header matches required specification exactly")
    void testLedgerHeader() {
        assertEquals("Ledger ID,Transaction ID,Account,Entry Type,Debit,Credit,Description,Timestamp",
                CsvExporter.LEDGER_HEADER);
    }

    @Test
    @DisplayName("CSV escaping handles commas, double quotes, and newlines properly (RFC 4180)")
    void testCsvEscapingSpecialCharacters() {
        // Commas
        assertEquals("\"Acme, Inc.\"", CsvExporter.escapeCell("Acme, Inc."));

        // Quotes doubled
        assertEquals("\"He said \"\"Urgent\"\"\"", CsvExporter.escapeCell("He said \"Urgent\""));

        // Newlines
        assertEquals("\"Line 1\nLine 2\"", CsvExporter.escapeCell("Line 1\nLine 2"));

        // Plain text unchanged
        assertEquals("NORMAL_TEXT", CsvExporter.escapeCell("NORMAL_TEXT"));

        // Null value returns empty string
        assertEquals("", CsvExporter.escapeCell(null));
    }

    @Test
    @DisplayName("Formula injection: untrusted text starting with =, +, -, @, \\t is safely neutralized with a single quote")
    void testFormulaInjectionProtection() {
        // Formulas starting with =
        String eqSanitized = CsvExporter.sanitizeText("=cmd|' /C calc'!A0");
        assertEquals("'=cmd|' /C calc'!A0", eqSanitized);
        assertEquals("\"'=cmd|' /C calc'!A0\"", CsvExporter.escapeCell(eqSanitized));

        // Formulas starting with +
        String plusSanitized = CsvExporter.sanitizeText("+12345");
        assertEquals("'+12345", plusSanitized);
        assertEquals("\"'+12345\"", CsvExporter.escapeCell(plusSanitized));

        // Formulas starting with -
        String minusSanitized = CsvExporter.sanitizeText("-SUM(A1:A10)");
        assertEquals("'-SUM(A1:A10)", minusSanitized);
        assertEquals("\"'-SUM(A1:A10)\"", CsvExporter.escapeCell(minusSanitized));

        // Formulas starting with @
        String atSanitized = CsvExporter.sanitizeText("@SUM(A1:A10)");
        assertEquals("'@SUM(A1:A10)", atSanitized);
        assertEquals("\"'@SUM(A1:A10)\"", CsvExporter.escapeCell(atSanitized));

        // Tabs
        String tabSanitized = CsvExporter.sanitizeText("\t=1+1");
        assertEquals("'\t=1+1", tabSanitized);

        // Standard text without formula triggers is not modified
        assertEquals("Customer Payment", CsvExporter.sanitizeText("Customer Payment"));
        assertEquals("ACC-1001", CsvExporter.sanitizeText("ACC-1001"));
    }

    @Test
    @DisplayName("Preserves exact decimal representation and does not sanitize legitimate numeric amounts")
    void testPreserveLegitimateNumericAmounts() {
        assertEquals("100.00", CsvExporter.formatDecimal(new BigDecimal("100")));
        assertEquals("12345.60", CsvExporter.formatDecimal(new BigDecimal("12345.6")));
        assertEquals("0.00", CsvExporter.formatDecimal(BigDecimal.ZERO));
        assertEquals("9999999.99", CsvExporter.formatDecimal(new BigDecimal("9999999.99")));

        // Legitimate negative numeric amounts are not prefixed with single quotes
        assertEquals("-50.00", CsvExporter.formatDecimal(new BigDecimal("-50.00")));
    }

    @Test
    @DisplayName("Preserves Unicode characters such as Indian Rupee (₹) and accented glyphs")
    void testUnicodeAndCurrencyHandling() throws IOException {
        Transaction tx = new Transaction(
                "TXN-20261029-UNICODE",
                TransactionType.DEPOSIT,
                "SYSTEM-CASH",
                "ACC-101",
                new BigDecimal("5000.00"),
                TransactionStatus.SUCCESS,
                "UPI Payment of ₹5,000 for Café & Résumé",
                TEST_TIME
        );

        String csv = CsvExporter.exportTransactionsToCsv(List.of(tx));
        assertTrue(csv.contains("₹5,000 for Café & Résumé"));

        Path exportFile = tempDir.resolve("unicode_test.csv");
        CsvExporter.writeTransactions(exportFile, List.of(tx));

        byte[] rawBytes = Files.readAllBytes(exportFile);
        // Verify UTF-8 BOM: 0xEF, 0xBB, 0xBF
        assertEquals((byte) 0xEF, rawBytes[0]);
        assertEquals((byte) 0xBB, rawBytes[1]);
        assertEquals((byte) 0xBF, rawBytes[2]);

        String readBack = Files.readString(exportFile, StandardCharsets.UTF_8);
        assertTrue(readBack.contains("₹5,000 for Café & Résumé"));
    }

    @Test
    @DisplayName("Ledger export properly separates Debit and Credit columns")
    void testLedgerDebitAndCreditSeparation() {
        LedgerEntry debitEntry = new LedgerEntry(
                1L,
                "TXN-001",
                "ACC-DEBIT",
                LedgerEntryType.DEBIT,
                new BigDecimal("750.50"),
                "Cash withdrawal leg",
                TEST_TIME
        );

        LedgerEntry creditEntry = new LedgerEntry(
                2L,
                "TXN-001",
                "SYSTEM-CASH",
                LedgerEntryType.CREDIT,
                new BigDecimal("750.50"),
                "Cash pool credit leg",
                TEST_TIME
        );

        String debitRow = CsvExporter.formatLedgerRow(debitEntry);
        // Debit entry should have 750.50 in Debit column and empty in Credit column
        assertTrue(debitRow.contains(",DEBIT,750.50,,"));

        String creditRow = CsvExporter.formatLedgerRow(creditEntry);
        // Credit entry should have empty in Debit column and 750.50 in Credit column
        assertTrue(creditRow.contains(",CREDIT,,750.50,"));
    }

    @Test
    @DisplayName("Filtered transactions export only matching records")
    void testFilteredTransactionsExport() {
        Transaction t1 = new Transaction("TXN-1", TransactionType.DEPOSIT, "SYSTEM-CASH", "ACC-1",
                new BigDecimal("100.00"), TransactionStatus.SUCCESS, "Deposit 1", TEST_TIME);
        Transaction t2 = new Transaction("TXN-2", TransactionType.WITHDRAWAL, "ACC-1", "SYSTEM-CASH",
                new BigDecimal("50.00"), TransactionStatus.FAILED, "Failed withdrawal", TEST_TIME);
        Transaction t3 = new Transaction("TXN-3", TransactionType.TRANSFER, "ACC-1", "ACC-2",
                new BigDecimal("20.00"), TransactionStatus.SUCCESS, "Transfer", TEST_TIME);

        // Filter: only SUCCESS transactions
        List<Transaction> successOnly = List.of(t1, t2, t3).stream()
                .filter(t -> t.getStatus() == TransactionStatus.SUCCESS)
                .toList();

        String csv = CsvExporter.exportTransactionsToCsv(successOnly);
        assertTrue(csv.contains("TXN-1"));
        assertFalse(csv.contains("TXN-2"));
        assertTrue(csv.contains("TXN-3"));

        // Header + 2 data rows = 3 lines
        String[] lines = csv.split("\r\n");
        assertEquals(3, lines.length);
    }

    @Test
    @DisplayName("Filtered ledger export only matching records")
    void testFilteredLedgerExport() {
        LedgerEntry e1 = new LedgerEntry(10L, "TXN-1", "ACC-AAA", LedgerEntryType.DEBIT, new BigDecimal("10.00"), "d1", TEST_TIME);
        LedgerEntry e2 = new LedgerEntry(11L, "TXN-1", "ACC-BBB", LedgerEntryType.CREDIT, new BigDecimal("10.00"), "c1", TEST_TIME);
        LedgerEntry e3 = new LedgerEntry(12L, "TXN-2", "ACC-CCC", LedgerEntryType.DEBIT, new BigDecimal("20.00"), "d2", TEST_TIME);

        // Filter: only for account ACC-AAA
        List<LedgerEntry> forAaa = List.of(e1, e2, e3).stream()
                .filter(e -> e.getAccountId().equals("ACC-AAA"))
                .toList();

        String csv = CsvExporter.exportLedgerToCsv(forAaa);
        assertTrue(csv.contains("ACC-AAA"));
        assertFalse(csv.contains("ACC-BBB"));
        assertFalse(csv.contains("ACC-CCC"));

        String[] lines = csv.split("\r\n");
        assertEquals(2, lines.length); // Header + 1 record
    }

    @Test
    @DisplayName("Write transactions to file and verify round-trip durability and content")
    void testWriteTransactionsToFileAndReadBack() throws IOException {
        Transaction tx = new Transaction(
                "TXN-ROUNDTRIP-01",
                TransactionType.TRANSFER,
                "ACC-SRC",
                "ACC-DST",
                new BigDecimal("1234.56"),
                TransactionStatus.SUCCESS,
                "Quarterly settlement, confidential",
                TEST_TIME
        );

        Path file = tempDir.resolve("transactions_roundtrip.csv");
        CsvExporter.writeTransactions(file, List.of(tx));

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.startsWith(CsvExporter.UTF8_BOM + CsvExporter.TRANSACTION_HEADER));
        assertTrue(content.contains("TXN-ROUNDTRIP-01"));
        assertTrue(content.contains("1234.56"));
        assertTrue(content.contains("\"Quarterly settlement, confidential\""));
    }

    @Test
    @DisplayName("Write ledger entries to file and verify round-trip durability and content")
    void testWriteLedgerToFileAndReadBack() throws IOException {
        LedgerEntry entry = new LedgerEntry(
                99L,
                "TXN-ROUNDTRIP-02",
                "ACC-TARGET",
                LedgerEntryType.DEBIT,
                new BigDecimal("9876.50"),
                "Audit adjustment",
                TEST_TIME
        );

        Path file = tempDir.resolve("ledger_roundtrip.csv");
        CsvExporter.writeLedger(file, List.of(entry));

        assertTrue(Files.exists(file));
        assertTrue(Files.size(file) > 0);

        String content = Files.readString(file, StandardCharsets.UTF_8);
        assertTrue(content.startsWith(CsvExporter.UTF8_BOM + CsvExporter.LEDGER_HEADER));
        assertTrue(content.contains("99,TXN-ROUNDTRIP-02,ACC-TARGET,DEBIT,9876.50,,Audit adjustment,2026-10-29 14:30:45"));
    }
}
