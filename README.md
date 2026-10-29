# VaultLedger — Core Banking Account Lifecycle & Ledger System

[![Java Version](https://img.shields.io/badge/Java-17%2B-blue.svg)](https://www.oracle.com/java/technologies/javase/jdk17-archive-downloads.html)
[![JavaFX](https://img.shields.io/badge/JavaFX-21.0.5-orange.svg)](https://openjfx.io/)
[![Database](https://img.shields.io/badge/SQLite-3.46.1.0-lightgrey.svg)](https://sqlite.org/)
[![Build Tool](https://img.shields.io/badge/Maven-3.x-red.svg)](https://maven.apache.org/)
[![Tests](https://img.shields.io/badge/Tests-110%20Passed-brightgreen.svg)]()
[![Repository](https://img.shields.io/badge/GitHub-jeedijoshua--art%2Foopsmp-blue.svg)](https://github.com/jeedijoshua-art/oopsmp)

> **Academic Notice:** **VaultLedger** is an academic desktop banking simulation engineered to demonstrate core banking principles, double-entry bookkeeping, strict account lifecycle states, atomic transactions, and thread-safe concurrency controls. It is designed strictly for educational and evaluation purposes and is not intended for production banking deployments or real customer funds.

**Official Repository:** [https://github.com/jeedijoshua-art/oopsmp](https://github.com/jeedijoshua-art/oopsmp)

---

## Table of Contents

1. [Project Overview](#1-project-overview)
2. [Features](#2-features)
3. [Technology Stack](#3-technology-stack)
4. [Project Architecture](#4-project-architecture)
5. [Prerequisites](#5-prerequisites)
6. [Clone and Run on macOS](#6-clone-and-run-on-macos)
7. [Clone and Run on Windows](#7-clone-and-run-on-windows)
8. [First Launch and Demo Data](#8-first-launch-and-demo-data)
9. [Complete UI User Guide](#9-complete-ui-user-guide)
10. [Demonstration Scenarios](#10-demonstration-scenarios)
11. [Testing](#11-testing)
12. [Troubleshooting](#12-troubleshooting)
13. [GitHub Repository & Contribution Workflow](#13-github-repository--contribution-workflow)
14. [Academic Disclaimer](#14-academic-disclaimer)

---

## 1. Project Overview

**VaultLedger** is a core banking desktop simulation application developed in **Java 17**, **JavaFX 21**, and **SQLite 3**. It models how modern financial institutions manage account lifecycles, enforce relational integrity, execute atomic ACID transactions, and maintain an immutable, balanced double-entry accounting ledger.

The application allows students, evaluators, and developers to observe how transactions move through the system:
- From the **JavaFX presentation tier**,
- Across the **Service validation tier**,
- Through the **AccountLockManager concurrency coordinator**,
- Into the **JDBC persistence tier**,
- And finally onto an immutable, self-auditing **double-entry general ledger**.

---

## 2. Features

Every feature listed below is verified and implemented in the application source code:

* **Executive Dashboard & Real-Time Metrics:** Live metric cards summarizing Total Accounts, Active Accounts, Visible Banks, Aggregate Customer Balance, and recent financial activity.
* **Customer Onboarding & Account Creation:** Account registration form with client-side and domain validation:
  - Account ID (alphanumeric, auto-generated or custom).
  - Customer full name.
  - Age restriction (18 to 120 years).
  - Optional contact fields (valid email format, 10-digit phone number).
  - Bank affiliation and account classification (`SAVINGS` or `CURRENT`).
  - Non-negative initial deposit.
* **Strict Account Lifecycle Management:** Formal three-state machine:
  - **`ACTIVE`:** Eligible for deposits, withdrawals, and transfers.
  - **`INACTIVE`:** Dormant state; financial transactions are blocked; can be reactivated.
  - **`CLOSED`:** Terminal state; requires zero balance; cannot be reopened; eligible for permanent deletion.
* **Atomic Deposit Operations:** Credit money into active accounts backed by the system cash pool (`SYSTEM-CASH`) with automated double-entry ledgering.
* **Protected Withdrawal Operations:** Debit funds from active accounts with strict overdraft prevention, live before/after calculation preview, and immediate cash-offset bookkeeping.
* **Multi-Account & Inter-Bank Transfers:** Supports both intra-bank (same bank) and inter-bank (cross-institution) transfers coordinated by an ordered `AccountLockManager` that eliminates locking race conditions and deadlocks regardless of transfer direction.
* **Transaction Receipts with Confirmed Balance:** Immediate modal receipt for every completed transaction displaying Transaction ID, Status, Type, From Account, To Account, Transfer Scope (Intra-Bank vs Inter-Bank), Amount, Timestamp, Description, and Confirmed Post-Transaction Balances for all involved accounts.
* **Strict ACID Atomicity & Rollback:** All operations execute within transactional database scopes that auto-rollback on any unhandled exception or constraint failure, leaving no partial state or unlinked ledger entries.
* **Double-Entry General Ledger & Mathematical Audit:** Relational ledger capturing paired `DEBIT` and `CREDIT` legs for 100% of operations. Includes an **Integrity & Audit Reconciliation** dashboard that verifies both global journal invariance (`Total Debits == Total Credits`) and per-account mathematical reconciliation (`Actual Balance == Opening Balance + Credits - Debits`).
* **Searchable Historical Activity:** Searchable transaction history with real-time text query and dropdown filtering by `TransactionType` and `TransactionStatus`.
* **Zero-Configuration SQLite Persistence:** Automated schema initialization, schema migration, index creation, and foreign key cascades stored in a local SQLite file (`data/core-banking.db`).
* **Idempotent Demo Data Seeding:** One-click seeding of sample bank and accounts with realistic initial transactions.
* **Granular Database Maintenance:** Ability to safely purge only demo-tagged records or wipe the entire workspace with confirmation guards.
* **In-App GitHub Repository Link:** Direct one-click access to the project repository on GitHub via desktop browser integration.

---

## 3. Technology Stack

| Layer | Component | Version / Specification | Details |
| :--- | :--- | :--- | :--- |
| **Language** | Java (JDK) | 17 LTS | Modern LTS Java syntax (records, pattern matching, sealed types) |
| **GUI Framework** | OpenJFX (JavaFX) | 21.0.5 | Native desktop UI, Controls, Layouts, FXML, Stage/Scene architecture |
| **Database** | SQLite | 3.46.1.0 | Embedded relational engine via `org.xerial:sqlite-jdbc` |
| **Build System** | Apache Maven | 3.8+ | Dependency management, build lifecycle, JavaFX Maven plugin |
| **Testing** | JUnit Jupiter | 5.11.0 | Unit, integration, concurrency, rollback, and UI test suite (110 tests) |
| **Concurrency** | Java Concurrency | `java.util.concurrent` | `ReentrantLock` striping and lexicographical ordering |
| **Styling** | JavaFX CSS | Custom 3.0 Design | VaultLedger Navy (`#0A1728`), Teal (`#009C9C`), and High-Contrast Light (`#FFFFFF`) |

---

## 4. Project Architecture

VaultLedger follows an enterprise four-tier layered architecture:

```
┌─────────────────────────────────────────────────────────────┐
│                    JavaFX Presentation Layer                │
│       AppShell, DialogWindow, AlertUtil, FormatUtil         │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                      Service Logic Layer                    │
│   AccountService, TransactionService, LedgerService,        │
│   BankService, DatabaseManagementService                    │
│   ┌─────────────────────────────────────────────────────┐   │
│   │   AccountLockManager (Deadlock-Free Locking)        │   │
│   └─────────────────────────────────────────────────────┘   │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                    Repository Access Layer                  │
│   AccountRepository, TransactionRepository,                 │
│   LedgerRepository, BankRepository                          │
└──────────────────────────────┬──────────────────────────────┘
                               │
┌──────────────────────────────▼──────────────────────────────┐
│                 Embedded Relational Database                │
│                 SQLite 3 (data/core-banking.db)             │
└─────────────────────────────────────────────────────────────┘
```

### Directory Structure

```text
oopsmp/
├── data/                                 # Auto-created SQLite database directory (git-ignored)
│   └── core-banking.db                   # Local SQLite database file
├── src/
│   ├── main/
│   │   ├── java/com/corebanking/
│   │   │   ├── config/                   # DB connection, transactions, seeder, initializer
│   │   │   │   ├── AppConfig.java
│   │   │   │   ├── DatabaseConnection.java
│   │   │   │   ├── DatabaseInitializer.java
│   │   │   │   ├── DatabaseTransaction.java
│   │   │   │   └── DemoDataSeeder.java
│   │   │   ├── enums/                    # AccountType, AccountStatus, TransactionType, LedgerEntryType
│   │   │   ├── exception/                # Domain banking exceptions (InsufficientFunds, etc.)
│   │   │   ├── model/                    # Immutable domain models (Account, Bank, Transaction, LedgerEntry)
│   │   │   ├── repository/               # JDBC DAOs with PreparedStatement mapping
│   │   │   ├── service/                  # Business services and interfaces
│   │   │   ├── transaction/              # AccountLockManager for lock ordering
│   │   │   ├── ui/                       # Presentation tier (AppShell)
│   │   │   │   └── util/                 # AlertUtil, FormatUtil
│   │   │   └── Main.java                 # Desktop application entry point
│   │   └── resources/
│   │       ├── css/
│   │       │   └── application.css       # High-contrast VaultLedger banking theme
│   │       └── database/
│   │           └── schema.sql            # Relational database table definitions and indices
│   └── test/java/com/corebanking/        # 110 automated unit, integration, concurrency, & UI tests
├── pom.xml                               # Maven project definition
├── userinst.md                           # Extended user manual and demonstration script
└── README.md                             # Project documentation (this file)
```

---

## 5. Prerequisites

Before installing, ensure that your system has:

1. **Java Development Kit (JDK) 17 or higher** installed.
2. **Apache Maven 3.8 or higher** installed.
3. **Git** installed.

### Verify Prerequisites

Open a terminal or command prompt and verify:

```bash
java -version
mvn -version
git --version
```

*Expected output: Java version `17.x` or higher, Maven `3.8.x` or higher.*

---

## 6. Clone and Run on macOS

### Step 1: Open Terminal and Clone the Repository

```bash
git clone https://github.com/jeedijoshua-art/oopsmp.git
cd oopsmp
```

### Step 2: Verify Java 17 and Maven

```bash
java -version
mvn -version
```

*(If Java 17 is missing on macOS, install it via Homebrew: `brew install openjdk@17 maven`)*

### Step 3: Compile and Launch the Application

```bash
mvn clean compile
mvn javafx:run
```

The desktop application window titled **"VaultLedger — Core Banking Account Lifecycle & Ledger System"** will launch automatically.

### Step 4: Run the Test Suite

```bash
mvn test
```

---

## 7. Clone and Run on Windows

### Step 1: Open PowerShell or Command Prompt

Clone the repository into your desired workspace folder:

```powershell
git clone https://github.com/jeedijoshua-art/oopsmp.git
cd oopsmp
```

### Step 2: Verify Java 17 and Maven on Windows

Ensure `JAVA_HOME` is configured in your System Environment Variables and both `java` and `mvn` are on your PATH:

```powershell
java -version
mvn -version
```

*(If missing on Windows, install via winget: `winget install EclipseAdoptium.Temurin.17.JDK` and `winget install Apache.Maven`)*

### Step 3: Launch the Application

```powershell
mvn clean compile
mvn javafx:run
```

### Step 4: Run Automated Tests

```powershell
mvn test
```

---

## 8. First Launch and Demo Data

### Initial State
On first launch, if no database exists, VaultLedger automatically creates `data/core-banking.db` and applies the schema from `schema.sql`. The system cash pool (`SYSTEM-CASH`) and system bank (`SYSTEM`) are provisioned automatically.

The initial dashboard displays:
- **Total Accounts:** `0`
- **Active Accounts:** `0`
- **Total Customer Balance:** `₹0.00`
- **Visible Banks:** `0`
- An empty transaction table prompting: *"Your deposits, withdrawals, and transfers will appear here."*

### Loading Demo Data
To immediately explore the system with realistic data:
1. Navigate to the **Dashboard**.
2. In the **Quick Actions** panel, click **`Load Demo Data`**.
3. A confirmation notification will appear: *"Demo Data Loaded: Sample bank, accounts, and transactions were created."*

### Verified Demo Records Created
The seeder (`DemoDataSeeder.java`) creates:
* **Bank:** Northstar Bank (Code: `NSB`)
* **Customer Accounts:**
  1. `ACC-JOSHUA` — Customer: **Joshua**, Type: `SAVINGS`, Initial: ₹1,000.00
  2. `ACC-ROSHAN` — Customer: **Roshan**, Type: `CURRENT`, Initial: ₹500.00
  3. `ACC-ALICE` — Customer: **Alice**, Type: `SAVINGS`, Initial: ₹250.00
* **5 Initial Real Transactions:**
  1. **Deposit** ₹500.00 to `ACC-JOSHUA` (Initial Deposit)
  2. **Deposit** ₹200.00 to `ACC-ROSHAN` (Initial Deposit)
  3. **Withdrawal** ₹100.00 from `ACC-JOSHUA` (ATM Withdrawal)
  4. **Transfer** ₹150.00 from `ACC-JOSHUA` to `ACC-ROSHAN` (Rent payment)
  5. **Transfer** ₹50.00 from `ACC-ROSHAN` to `ACC-ALICE` (Dinner)
* **Verified Ending Demo Balances:**
  - `ACC-JOSHUA`: **₹1,250.00**
  - `ACC-ROSHAN`: **₹800.00**
  - `ACC-ALICE`: **₹300.00**
* **10 Double-Entry General Ledger Entries:** Matched debits and credits totaling ₹1,000.00, fully balanced.

### Idempotence & Data Safety
- Loading demo data is **strictly idempotent**. It checks whether bank `NSB` already exists before executing. Clicking `Load Demo Data` multiple times will not duplicate accounts or corrupt balances.
- Demo records are tagged with `is_demo = 1` in the database, allowing granular cleanup without affecting user-created accounts.

---

## 9. Complete UI User Guide

### 1. Dashboard (`⊞ Dashboard`)
The primary monitoring overview:
- **Stat Cards:** Real-time metrics for Total Accounts, Active Accounts, Total Balance, and Visible Banks.
- **Quick Actions:** Shortcuts for `+ Deposit`, `- Withdraw`, `⇄ Transfer`, `Manage Accounts`, `Load Demo Data`, `Reset Demo Data`, and `Reset Entire Workspace`.
- **Recent Transactions:** The 8 most recent transactions with live status badges.

### 2. Accounts Overview (`👥 Accounts`)
A searchable, filterable directory of all customer accounts:
- **Search Bar:** Real-time search across account IDs and customer names.
- **Filters:** Dropdowns to filter by `AccountStatus` (`ACTIVE`, `INACTIVE`, `CLOSED`) and `AccountType` (`SAVINGS`, `CURRENT`).
- **Data Table:** Shows Account ID, Customer Name, Type, Balance, Status, and Bank.
- **Action Column:** Every row includes a **`View`** button. Clicking **`View`** or double-clicking the row opens **Account Details**.

### 3. Account Details (Lifecycle & Operations Hub)
The central operational screen for an individual account:
- **Hero Balance Display:** Prominently displays the current balance in large, high-contrast typography.
- **Status Badge:** Visual indicator of account status (`ACTIVE` in green, `INACTIVE` in amber, `CLOSED` in red).
- **Customer Information Card:** Account ID, Customer Name, Age, Email, Phone, Account Type, Bank, and Balance.
- **Account & Money Operations:**
  - **`+ Deposit`:** Opens the deposit dialog with this account preselected.
  - **`- Withdraw`:** Opens the withdrawal dialog with this account preselected.
  - **`⇄ Transfer`:** Opens the transfer dialog with this account preselected as the source.
  - **`Activate`:** Reopens an inactive or closed account.
  - **`Deactivate`:** Freezes an active account to prevent transactions.
  - **`Close Account`:** Permanently closes an account (requires a ₹0.00 balance).
  - **`Delete`:** Permanently deletes a closed account with no transaction history.
- **Recent Account Activity:** Historical transactions specifically involving this account.

### 4. Preferred Transaction Workflow
To perform any financial operation with maximum clarity and safety:
1. Open **`👥 Accounts`** from the left sidebar.
2. Select an account and click **`View`** (or double-click the row) to open **Account Details**.
3. Choose **`+ Deposit`**, **`- Withdraw`**, or **`⇄ Transfer`**.
4. The dialog opens with the account preselected:
   - For **Deposit**: Target account is preselected.
   - For **Withdraw**: Source account is preselected.
   - For **Transfer**: Source account is preselected; the destination dropdown dynamically filters to show only *other* active accounts.
5. Enter the amount. Observe the **Live Calculation Preview** showing before and after balances.
6. Click Submit.
7. Review the **Transaction Receipt** displaying full audit details.
8. Click **Close Receipt** (or dismiss the dialog).
9. The Account Details page immediately refreshes with the new balance and shows the new transaction in the activity table.

### 5. Transactions History (`⇄ Transactions`)
Comprehensive, searchable financial ledger:
- Filter by transaction ID, account ID, or description text.
- Filter by type (`DEPOSIT`, `WITHDRAWAL`, `TRANSFER`).
- Filter by status (`SUCCESS`, `FAILED`).
- Double-click any transaction row to inspect its formal receipt.

### 6. Double-Entry General Ledger (`📒 Ledger`)
Auditor and compliance screen:
- Displays individual `DEBIT` and `CREDIT` entries.
- Summarizes Total Debits vs Total Credits.
- Features an automated mathematical audit indicator: **`BALANCED`** (when Debits equal Credits) or **`CHECK REQUIRED`** (if mismatched).

---

## 10. Demonstration Scenarios

Use these 10 practical scenarios to demonstrate all capabilities during presentations or evaluation sessions:

### Scenario 1: First-Time Setup & Loading Demo Data
* **Objective:** Initialize the application and load sample records.
* **Steps:**
  1. Launch the app (`mvn javafx:run`).
  2. Click **`Load Demo Data`** on the Dashboard.
* **Expected Result:** Dashboard metrics immediately update: 3 accounts, 1 bank, and 5 transactions appear in the table.

### Scenario 2: Creating a New Customer Account
* **Objective:** Onboard a new customer with validation checks.
* **Steps:**
  1. Click **`✚ Create Account`** in the sidebar.
  2. Fill in: Customer Name: `Sarah Connor`, Age: `29`, Email: `sarah@skynet.com`, Phone: `9876543210`, Bank: `Northstar Bank`, Type: `SAVINGS`, Initial Deposit: `500.00`.
  3. Click **`Create Account`**.
* **Expected Result:** A success alert appears with the new Account ID. The view redirects to Accounts, listing Sarah Connor with an active status and ₹500.00 balance.

### Scenario 3: Depositing Funds (Streamlined Flow)
* **Objective:** Deposit funds using the account-first workflow.
* **Steps:**
  1. Open **`👥 Accounts`** → click **`View`** on `ACC-JOSHUA` (Balance: ₹1,250.00).
  2. Click **`+ Deposit`**.
  3. Notice `ACC-JOSHUA` is preselected. Enter Amount: `250.00`.
  4. Notice the live preview: `Current: ₹1,250.00 → New Balance: ₹1,500.00`.
  5. Click **`Deposit`** → Review the receipt → Click **`Close Receipt`**.
* **Expected Result:** Joshua's balance updates immediately to **₹1,500.00** on the screen.

### Scenario 4: Withdrawing Funds
* **Objective:** Withdraw money and observe real-time balance deductions.
* **Steps:**
  1. In Joshua's account details, click **`- Withdraw`**.
  2. Enter Amount: `300.00`, Description: `ATM Cash`.
  3. Notice preview: `Current: ₹1,500.00 → New Balance: ₹1,200.00`.
  4. Click **`Withdraw`** → Close receipt.
* **Expected Result:** Joshua's balance updates to **₹1,200.00**.

### Scenario 5: Transferring Funds Between Distinct Accounts
* **Objective:** Move money between two accounts and verify atomic dual-balance updates.
* **Steps:**
  1. In Joshua's account details, click **`⇄ Transfer`**.
  2. Notice `ACC-JOSHUA` is preselected as the source.
  3. Open the "To Account" dropdown: notice `ACC-JOSHUA` is **excluded**, displaying only `ACC-ROSHAN`, `ACC-ALICE`, and `Sarah Connor`.
  4. Select `ACC-ROSHAN` (Roshan). Enter Amount: `200.00`.
  5. Observe preview: `Joshua: ₹1,000.00 | Roshan: ₹1,000.00`.
  6. Click **`Transfer`** → Close receipt.
* **Expected Result:** Joshua's balance is ₹1,000.00. Navigating back to Accounts confirms Roshan's balance increased to ₹1,000.00.

### Scenario 6: Demonstrating Insufficient Funds Validation
* **Objective:** Verify overdraft protection.
* **Steps:**
  1. In Joshua's account details, click **`- Withdraw`**.
  2. Enter an amount exceeding his balance: `99999.00`.
  3. Notice the live warning: `Insufficient funds! Balance: ₹1,000.00 | Requested: ₹99,999.00`.
  4. Click **`Withdraw`**.
* **Expected Result:** The operation is declined with an error alert: *"Transaction declined: insufficient funds."* No balances change.

### Scenario 7: Account Lifecycle State Management
* **Objective:** Test deactivation, transaction blocking, and reactivation.
* **Steps:**
  1. In an account details page, click **`Deactivate`**. Status changes to `INACTIVE`.
  2. Notice the `+ Deposit`, `- Withdraw`, and `⇄ Transfer` buttons are automatically disabled.
  3. Click **`Activate`**. Status changes back to `ACTIVE` and transaction buttons re-enable.

### Scenario 8: Closing an Account (Zero-Balance Rule)
* **Objective:** Verify that only accounts with a zero balance can be closed.
* **Steps:**
  1. Attempt to click **`Close Account`** on an account with funds: the system warns or blocks closure.
  2. Withdraw the exact remaining balance so the account has ₹0.00.
  3. Click **`Close Account`** and confirm.
* **Expected Result:** Status transitions to `CLOSED`.

### Scenario 9: Inspecting the Double-Entry Ledger
* **Objective:** Audit the accounting integrity of all transactions.
* **Steps:**
  1. Click **`📒 Ledger`** in the sidebar.
  2. Review the list of paired `DEBIT` and `CREDIT` entries.
  3. Check the audit banner at the top.
* **Expected Result:** Total Debits strictly equal Total Credits, and the green **`BALANCED`** badge is displayed.

### Scenario 10: Application Restart & SQLite Persistence
* **Objective:** Confirm data persists across restarts.
* **Steps:**
  1. Note current account balances and close the application window.
  2. Relaunch via `mvn javafx:run`.
  3. Open Accounts and Ledger.
* **Expected Result:** All accounts, balances, transactions, and ledger entries remain intact.

---

## 11. Testing

VaultLedger includes a suite of automated unit, integration, and UI tests executed via JUnit 5:

```bash
# Run all tests
mvn test

# Run the Core Banking Engine Audit Suite
mvn test -Dtest=CoreBankingEngineAuditTest

# Run a specific test class
mvn test -Dtest=TransferServiceTest
```

### Verified Test Results
```text
Tests run: 110, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### What the Tests Cover
1. **Core Banking Engine Audit (`CoreBankingEngineAuditTest`):** 16 comprehensive compliance scenarios including valid deposits, withdrawals, overdraft prevention, invalid amount guards, inactive/closed account handling, intra-bank transfers, inter-bank transfers across distinct institutions, self-transfer rejection, dual-entry debit=credit validation, simulated crash and failure-injection rollback (verifying 0 partial records), concurrent limited-funds withdrawals (preventing negative balances), multi-threaded transfer liquidity conservation, global transaction ID uniqueness, SQLite disk persistence across connection reopenings, receipt-to-database match with post-transaction balance confirmation, and ledger reconciliation discrepancy detection.
2. **Domain Models (`DomainModelTest`):** Validation of Account, Bank, Transaction, LedgerEntry, MoneyUtil, and `equals`/`hashCode` identity.
3. **Account Services (`AccountServiceTest`, `AccountManagementTest`):** Account onboarding, age restrictions, email validation, and state machine transitions (`ACTIVE`, `INACTIVE`, `CLOSED`).
4. **Transaction Services (`TransactionServiceTest`, `TransferServiceTest`):** Deposit calculations, withdrawal limits, overdraft rejection, and atomic transfers.
5. **Double-Entry Ledger (`LedgerServiceTest`):** Validation of dual debit/credit entries, balanced ledger invariant enforcement, and reconciliation reporting.
6. **Concurrency & Deadlock Prevention (`ConcurrencyIntegrationTest`):** Multi-threaded concurrent deposits and transfers across threads to prove thread safety and absence of deadlocks.
7. **Data Access Layer (`RepositoryIntegrationTest`, `DatabaseLayerTest`):** SQLite schema creation, foreign key constraints, column additions, and JDBC query mapping.
8. **UI Presentation Tier (`AppShellUITest`, `TransactionDialogTest`):** JavaFX node creation, modal dialogs, account preselection, live calculation previews, and GitHub link verification.

---

## 12. Troubleshooting

| Issue | Probable Cause | Verified Resolution |
| :--- | :--- | :--- |
| **`java: command not found`** | JDK 17 is not installed or not on the system PATH. | Install JDK 17 (Eclipse Temurin or OpenJDK) and ensure `JAVA_HOME/bin` is added to your PATH environment variable. |
| **`mvn: command not found`** | Apache Maven is not installed or not on PATH. | Install Maven via `brew install maven` (macOS) or `winget install Apache.Maven` (Windows). |
| **JavaFX runtime components missing** | Running `java com.corebanking.Main` directly without JavaFX modules. | Always launch using Maven: `mvn javafx:run`. Maven resolves JavaFX dependencies automatically. |
| **Empty tables on initial launch** | Fresh database with no records. | On the Dashboard, click **`Load Demo Data`** under Quick Actions. |
| **`Insufficient funds` error** | Withdrawal or transfer exceeds account balance. | Enter an amount less than or equal to the account balance. |
| **Account dropdown empty during transfer** | System has fewer than two active accounts. | Ensure at least two accounts are created and in the `ACTIVE` state. |
| **Database corruption or dirty test state** | Partial edits during manual testing. | Click **`Reset Entire Local Workspace`** on the Dashboard (type `RESET ALL DATA` to confirm), or close the app and delete `data/core-banking.db`. It will auto-regenerate on next launch. |

---

## 13. GitHub Repository & Contribution Workflow

**Remote Repository:** [https://github.com/jeedijoshua-art/oopsmp.git](https://github.com/jeedijoshua-art/oopsmp.git)

### Teammate Git Workflow

When contributing changes to the project:

```bash
# 1. Ensure you are on main and up to date
git checkout main
git pull origin main

# 2. Create a descriptive feature branch
git checkout -b feature/your-feature-name

# 3. Make changes and verify tests pass
mvn clean test

# 4. Check status and stage only intended files
git status
git add src/main/... src/test/...

# 5. Commit with a meaningful message
git commit -m "feat: description of feature"

# 6. Push to remote
git push -u origin feature/your-feature-name
```

*Note: For pushing to GitHub, ensure your Git Credential Manager or Personal Access Token (PAT) is configured. Never hardcode credentials into the codebase.*

---

## 14. Academic Disclaimer

**VaultLedger** is developed solely for educational coursework and academic evaluation. It does not possess financial certifications, PCI-DSS compliance, or integration with real-world banking payment gateways. Do not use this software for processing real financial transactions.

---

*VaultLedger — Core Banking Account Lifecycle & Ledger System*  
*Repository: [https://github.com/jeedijoshua-art/oopsmp](https://github.com/jeedijoshua-art/oopsmp)*
