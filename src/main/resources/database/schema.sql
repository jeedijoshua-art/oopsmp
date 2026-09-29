CREATE TABLE IF NOT EXISTS banks (
    bank_id INTEGER PRIMARY KEY AUTOINCREMENT,
    bank_code TEXT NOT NULL UNIQUE,
    bank_name TEXT NOT NULL,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS accounts (
    account_id TEXT PRIMARY KEY,
    customer_name TEXT NOT NULL,
    bank_id INTEGER NOT NULL,
    account_type TEXT NOT NULL,
    balance TEXT NOT NULL DEFAULT '0.00',
    status TEXT NOT NULL DEFAULT 'ACTIVE',
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (bank_id) REFERENCES banks (bank_id),
    CHECK (status IN ('ACTIVE', 'INACTIVE', 'CLOSED'))
);

CREATE TABLE IF NOT EXISTS transactions (
    transaction_id TEXT PRIMARY KEY,
    transaction_type TEXT NOT NULL,
    from_account TEXT,
    to_account TEXT,
    amount TEXT NOT NULL,
    status TEXT NOT NULL,
    description TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (from_account) REFERENCES accounts (account_id),
    FOREIGN KEY (to_account) REFERENCES accounts (account_id)
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    ledger_entry_id INTEGER PRIMARY KEY AUTOINCREMENT,
    transaction_id TEXT NOT NULL,
    account_id TEXT NOT NULL,
    entry_type TEXT NOT NULL,
    amount TEXT NOT NULL,
    description TEXT,
    created_at TEXT NOT NULL DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (transaction_id) REFERENCES transactions (transaction_id),
    FOREIGN KEY (account_id) REFERENCES accounts (account_id),
    CHECK (entry_type IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX IF NOT EXISTS idx_accounts_bank_id ON accounts (bank_id);
CREATE INDEX IF NOT EXISTS idx_transactions_created_at ON transactions (created_at);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_account_id ON ledger_entries (account_id);
CREATE INDEX IF NOT EXISTS idx_ledger_entries_transaction_id ON ledger_entries (transaction_id);
