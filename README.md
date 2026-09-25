# Core Banking Ledger System

## 1. Overview
This is an academic core banking ledger simulation implemented using Java, JavaFX, Maven, and SQLite. 

It demonstrates:
- Account lifecycle management (Active, Inactive, Closed)
- Financial operations (Deposits, Withdrawals, Transfers)
- Transaction history recording
- Immutable, accounting-style ledger records
- Extensive validation constraints
- Database persistence with robust foreign keys
- Account-level ReentrantLock concurrency to prevent race conditions during transfers

**Note:** This is an academic simulation and not intended as production banking infrastructure.

## 2. Features

### Dashboard
- Total customer accounts
- Active accounts (ready to transact)
- Total transactions
- Total customer balances across all accounts
- Quick action navigation
- Recent transaction summary

### Accounts
- Global account search
- Filtering by Status and Type
- Tabular display with sortable columns
- Deep-dive Account Details screen
- Lifecycle operations (Activate, Deactivate, Close)

### Transactions
- Deposit funds
- Withdraw funds
- Transfer funds between active accounts
- Form validation (sufficient balance, correct state)
- Detailed transaction history and success receipts

### Ledger
- Immutable double-entry accounting-style records
- Debit and credit tracking
- Balanced transaction validation
- Ledger history tied directly to transactions

### Validation
- Monetary validations (positive amounts, non-zero)
- Strict state-based limitations (no transactions on closed accounts)
- Database-level isolation

### Demo Data
The application includes a `DemoDataSeeder` that safely populates a predictable set of banks, accounts, and transactions for demonstration and testing purposes. It ensures you have an immediate working dataset upon clicking "Load Demo Data".

## 3. Tech Stack
- **Java**: 17
- **JavaFX**: 21.0.5
- **Build Tool**: Maven 3.x
- **Database**: SQLite (via `sqlite-jdbc` 3.46.1.0)
- **Testing**: JUnit 5.11.0
- **Styling**: Vanilla CSS

## 4. Project Structure
```text
src/
├── main/
│   ├── java/
│   │   └── com/corebanking/
│   │       ├── config/        # Database & Seeding config
│   │       ├── enums/         # Status and Type enums
│   │       ├── exception/     # Custom domain exceptions
│   │       ├── model/         # Domain records/entities
│   │       ├── repository/    # SQLite DAOs
│   │       ├── service/       # Business logic layer
│   │       ├── transaction/   # Concurrency/Locking utilities
│   │       ├── ui/            # JavaFX AppShell and views
│   │       ├── util/          # Formatting and generators
│   │       └── Main.java      # Application entry point
│   └── resources/
│       └── css/               # Application stylesheets
└── test/
    └── java/
        └── com/corebanking/   # Comprehensive unit/integration tests
```

## 5. Requirements
To build and run this project, you need:
- JDK 17
- Maven

Verify your environment:
```bash
java -version
mvn -version
```

## 6. Clone
```bash
git clone https://github.com/jeedijoshua-art/oopsmp.git
cd oopsmp
```

## 7. Build and Test
To compile the project and execute the comprehensive 84-test suite:
```bash
mvn clean test
```
This ensures the database logic, service validations, and concurrency controls are functioning properly.

## 8. Run
Launch the JavaFX desktop application using:
```bash
mvn javafx:run
```

## 9. Demo Workflow
1. Launch the application (`mvn javafx:run`).
2. Open the **Dashboard**.
3. Click **Load Demo Data** (this seeds users like Joshua, Roshan, and Alice).
4. Navigate to **Accounts** to view the seeded accounts.
5. Double-click an account (or click **View**) to open Account Details.
6. Perform a **Deposit** or **Withdrawal**.
7. Perform a **Transfer** to another account.
8. Open **Transactions** to view the receipts.
9. Open **Ledger** to observe the immutable double-entry debit/credit logs.
10. Verify balances reflect the operations accurately.

## 10. Testing
- **Automated Tests**: Over 80 JUnit tests validating repositories, services, and utilities.
- **Manual Acceptance Tests**: Verified UI flows and constraints.
- **Database Integrity**: Tests enforce PRAGMA rules and rollback handling.
- **JavaFX Startup**: Verified cross-platform execution via the Maven plugin.

## 11. Database
- The application uses **SQLite**.
- A local database file is automatically initialized in the `database/` directory.
- `DatabaseConnection.java` enforces `PRAGMA foreign_keys = ON` to guarantee structural integrity.
- `schema.sql` handles initial table creation cleanly.

## 12. Architecture
The application implements a clean, layered architecture:
```text
UI (JavaFX)  →  Service Layer (Business Logic)  →  Repository (Data Access)  →  SQLite
```
**Concurrency**: The `AccountLockManager` enforces strict, alphabetically-ordered `ReentrantLock` acquisition during transfers to prevent deadlocks in high-concurrency environments.

## 13. Contributing for Teammates
1. Clone the repository.
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Make your changes.
4. Run the test suite: `mvn clean test`
5. Verify the UI: `mvn javafx:run`
6. Commit your changes: `git commit -m "feat: description"`
7. Push your branch: `git push origin feature/your-feature`
8. Open a Pull Request.

## 14. Troubleshooting

### Maven not found
Ensure Maven is installed and its `bin/` directory is in your system's PATH.

### Java version mismatch
This project requires JDK 17. Verify your `JAVA_HOME` environment variable points to a JDK 17 installation.

### JavaFX startup problem
Always run the application through Maven (`mvn javafx:run`) rather than trying to execute the JAR directly, as Maven handles the JavaFX module path automatically.

### Database issue
If the database enters an invalid state during testing, simply delete the `database/` directory. The application will cleanly recreate the schema upon the next launch.
