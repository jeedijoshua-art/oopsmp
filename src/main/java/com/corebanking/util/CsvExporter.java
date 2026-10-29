package com.corebanking.util;

import com.corebanking.enums.LedgerEntryType;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Utility for exporting VaultLedger transactions and double-entry ledger records
 * to RFC 4180-compliant CSV with UTF-8 encoding, exact decimal preservation,
 * and spreadsheet formula-injection protection (OWASP/CWE-1236).
 */
public final class CsvExporter {

    public static final String TRANSACTION_HEADER =
            "Transaction ID,Type,From Account,To Account,Amount,Status,Timestamp,Description";

    public static final String LEDGER_HEADER =
            "Ledger ID,Transaction ID,Account,Entry Type,Debit,Credit,Description,Timestamp";

    public static final String LINE_SEPARATOR = "\r\n";
    public static final String UTF8_BOM = "\uFEFF";

    private static final DateTimeFormatter TIMESTAMP_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private CsvExporter() {
    }

    /**
     * Sanitizes untrusted text fields to protect against spreadsheet formula injection (CSV Injection).
     * If a cell begins with '=', '+', '-', '@', '\t', or '\r', it is neutralized with a single quote prefix
     * so spreadsheet engines (Excel, Sheets, Calc) treat it as plain text instead of executing a formula.
     * Legitimate numeric amounts are formatted separately and not treated as text.
     */
    public static String sanitizeText(String text) {
        if (text == null || text.isEmpty()) {
            return "";
        }
        char firstChar = text.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' || firstChar == '@'
                || firstChar == '\t' || firstChar == '\r') {
            return "'" + text;
        }
        return text;
    }

    /**
     * Escapes a cell according to RFC 4180 rules.
     * If the cell contains commas, quotes, line breaks, leading/trailing whitespace,
     * or starts with a quote/neutralized formula prefix, it is enclosed in double quotes
     * with internal quotes doubled (" -> "").
     */
    public static String escapeCell(String value) {
        if (value == null) {
            return "";
        }
        boolean needsQuotes = value.contains(",")
                || value.contains("\"")
                || value.contains("\n")
                || value.contains("\r")
                || value.startsWith(" ")
                || value.endsWith(" ")
                || value.startsWith("'");

        if (needsQuotes) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Formats a monetary amount using exact BigDecimal representation (2 decimal places).
     * Does NOT apply formula sanitization, ensuring positive and negative numbers remain
     * valid numeric values in spreadsheets.
     */
    public static String formatDecimal(BigDecimal amount) {
        if (amount == null) {
            return "";
        }
        return amount.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * Formats a timestamp consistently in ISO-like format: yyyy-MM-dd HH:mm:ss.
     */
    public static String formatTimestamp(LocalDateTime timestamp) {
        if (timestamp == null) {
            return "";
        }
        return timestamp.format(TIMESTAMP_FORMATTER);
    }

    /**
     * Formats a single transaction row.
     */
    public static String formatTransactionRow(Transaction transaction) {
        Objects.requireNonNull(transaction, "Transaction must not be null");
        return String.join(",",
                escapeCell(sanitizeText(transaction.getTransactionId())),
                escapeCell(sanitizeText(transaction.getTransactionType() != null ? transaction.getTransactionType().name() : "")),
                escapeCell(sanitizeText(transaction.getFromAccount())),
                escapeCell(sanitizeText(transaction.getToAccount())),
                formatDecimal(transaction.getAmount()),
                escapeCell(sanitizeText(transaction.getStatus() != null ? transaction.getStatus().name() : "")),
                formatTimestamp(transaction.getCreatedAt()),
                escapeCell(sanitizeText(transaction.getDescription()))
        );
    }

    /**
     * Formats a single ledger entry row.
     */
    public static String formatLedgerRow(LedgerEntry entry) {
        Objects.requireNonNull(entry, "Ledger entry must not be null");
        boolean isDebit = entry.getEntryType() == LedgerEntryType.DEBIT;
        boolean isCredit = entry.getEntryType() == LedgerEntryType.CREDIT;

        String debitValue = isDebit ? formatDecimal(entry.getAmount()) : "";
        String creditValue = isCredit ? formatDecimal(entry.getAmount()) : "";

        return String.join(",",
                String.valueOf(entry.getLedgerEntryId()),
                escapeCell(sanitizeText(entry.getTransactionId())),
                escapeCell(sanitizeText(entry.getAccountId())),
                escapeCell(sanitizeText(entry.getEntryType() != null ? entry.getEntryType().name() : "")),
                debitValue,
                creditValue,
                escapeCell(sanitizeText(entry.getDescription())),
                formatTimestamp(entry.getCreatedAt())
        );
    }

    /**
     * Generates the complete CSV string for a list of transactions.
     */
    public static String exportTransactionsToCsv(List<Transaction> transactions) {
        StringBuilder builder = new StringBuilder();
        builder.append(TRANSACTION_HEADER).append(LINE_SEPARATOR);
        if (transactions != null) {
            for (Transaction transaction : transactions) {
                builder.append(formatTransactionRow(transaction)).append(LINE_SEPARATOR);
            }
        }
        return builder.toString();
    }

    /**
     * Generates the complete CSV string for a list of ledger entries.
     */
    public static String exportLedgerToCsv(List<LedgerEntry> entries) {
        StringBuilder builder = new StringBuilder();
        builder.append(LEDGER_HEADER).append(LINE_SEPARATOR);
        if (entries != null) {
            for (LedgerEntry entry : entries) {
                builder.append(formatLedgerRow(entry)).append(LINE_SEPARATOR);
            }
        }
        return builder.toString();
    }

    /**
     * Writes transactions to a file encoded as UTF-8 with Byte Order Mark (BOM) for Excel compatibility.
     */
    public static void writeTransactions(Path path, List<Transaction> transactions) throws IOException {
        Objects.requireNonNull(path, "Target path must not be null");
        String csv = UTF8_BOM + exportTransactionsToCsv(transactions);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /**
     * Writes ledger entries to a file encoded as UTF-8 with Byte Order Mark (BOM) for Excel compatibility.
     */
    public static void writeLedger(Path path, List<LedgerEntry> entries) throws IOException {
        Objects.requireNonNull(path, "Target path must not be null");
        String csv = UTF8_BOM + exportLedgerToCsv(entries);
        if (path.getParent() != null) {
            Files.createDirectories(path.getParent());
        }
        Files.writeString(path, csv, StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }
}

