package com.corebanking.ui;

import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.model.Account;
import com.corebanking.model.Bank;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.LedgerRepository;
import com.corebanking.repository.TransactionRepository;
import com.corebanking.service.AccountService;
import com.corebanking.service.BankService;
import com.corebanking.service.TransactionService;
import com.corebanking.ui.util.AlertUtil;
import com.corebanking.ui.util.FormatUtil;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.Separator;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.math.BigDecimal;
import java.sql.Connection;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class AppShell {
    private final Connection connection;
    private final AccountService accountService;
    private final BankService bankService;
    private final TransactionService transactionService;
    private final BankRepository bankRepository;
    private final TransactionRepository transactionRepository;
    private final LedgerRepository ledgerRepository;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Label sectionTitle = new Label();
    private final Label sectionHint = new Label();
    private final ToggleGroup navigationGroup = new ToggleGroup();

    public AppShell(Connection connection, AccountService accountService, BankService bankService,
                    TransactionService transactionService) {
        this.connection = Objects.requireNonNull(connection);
        this.accountService = Objects.requireNonNull(accountService);
        this.bankService = Objects.requireNonNull(bankService);
        this.transactionService = Objects.requireNonNull(transactionService);
        this.bankRepository = new BankRepository(connection);
        this.transactionRepository = new TransactionRepository(connection);
        this.ledgerRepository = new LedgerRepository(connection);
    }

    public Parent build() {
        root.getStyleClass().add("app-root");
        root.setLeft(buildSidebar());
        root.setTop(buildHeader());
        root.setCenter(content);
        showDashboard();
        return root;
    }

    private Node buildSidebar() {
        VBox sidebar = new VBox(8);
        sidebar.getStyleClass().add("sidebar");
        sidebar.setPrefWidth(238);

        Label brand = new Label("NORTHSTAR BANK");
        brand.getStyleClass().add("brand");
        Label brandSub = new Label("Account lifecycle and ledger");
        brandSub.getStyleClass().add("brand-subtitle");
        VBox brandBox = new VBox(4, brand, brandSub);
        brandBox.getStyleClass().add("brand-box");

        VBox nav = new VBox(6);
        nav.getStyleClass().add("nav-list");
        nav.getChildren().addAll(
                navButton("Dashboard", "Overview", this::showDashboard),
                navButton("Accounts", "Customer accounts", this::showAccounts),
                navButton("Transactions", "Financial activity", this::showTransactions),
                navButton("Ledger", "Double-entry records", this::showLedger),
                navButton("Create Account", "Open a new account", this::showCreateAccount),
                navActionButton("New Transaction", "Start a deposit, withdrawal, or transfer", this::showNewTransaction));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        Label footer = new Label("ACADEMIC SIMULATION\nSQLite local workspace");
        footer.getStyleClass().add("sidebar-footer");
        sidebar.getChildren().addAll(brandBox, new Separator(), nav, spacer, footer);
        return sidebar;
    }

    private ToggleButton navButton(String text, String accessibilityText, Runnable action) {
        ToggleButton button = new ToggleButton(text);
        button.setToggleGroup(navigationGroup);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("nav-button");
        button.setAccessibleText(accessibilityText);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Button navActionButton(String text, String accessibilityText, Runnable action) {
        Button button = primaryButton("+  " + text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.setAccessibleText(accessibilityText);
        button.setOnAction(event -> action.run());
        return button;
    }

    private Node buildHeader() {
        HBox header = new HBox(18);
        header.getStyleClass().add("topbar");
        header.setAlignment(Pos.CENTER_LEFT);
        sectionTitle.getStyleClass().add("section-title");
        sectionHint.getStyleClass().add("section-hint");
        VBox titles = new VBox(3, sectionTitle, sectionHint);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label("LOCAL DATABASE  |  ONLINE");
        status.getStyleClass().add("system-status");
        header.getChildren().addAll(titles, spacer, status);
        return header;
    }

    private void showDashboard() {
        setSection("Dashboard", "A clear view of your banking workspace");
        List<Account> accounts = accountService.listAccounts();
        List<Transaction> transactions = transactionRepository.findAll();
        BigDecimal totalBalances = accounts.stream().map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        VBox page = page();
        HBox cards = new HBox(14,
                statCard("TOTAL ACCOUNTS", String.valueOf(accounts.size()), "Customer accounts"),
                statCard("ACTIVE ACCOUNTS", String.valueOf(accounts.stream()
                        .filter(account -> account.getStatus() == AccountStatus.ACTIVE).count()), "Ready to transact"),
                statCard("TRANSACTIONS", String.valueOf(transactions.size()), "All recorded activity"),
                statCard("CUSTOMER BALANCES", FormatUtil.money(totalBalances), "Across all accounts"));
        cards.getStyleClass().add("stat-row");
        HBox.setHgrow(cards.getChildren().get(3), Priority.ALWAYS);

        Label recentTitle = sectionLabel("Recent Transactions");
        TableView<Transaction> table = transactionTable(transactions.stream().limit(8).toList());
        table.setPlaceholder(emptyState("No transactions yet.\nDeposit, withdraw, or transfer to see activity here."));
        HBox quickActions = new HBox(10,
            sectionLabel("Quick Actions"),
            primaryButton("+ Deposit"),
            secondaryButton("- Withdraw"),
            secondaryButton("Transfer"),
            secondaryButton("Create Account"),
            secondaryButton("Load Demo Data"));
        Button deposit = (Button) quickActions.getChildren().get(1);
        Button withdraw = (Button) quickActions.getChildren().get(2);
        Button transfer = (Button) quickActions.getChildren().get(3);
        Button createAccount = (Button) quickActions.getChildren().get(4);
        Button loadDemoData = (Button) quickActions.getChildren().get(5);
        deposit.setOnAction(event -> showTransactionDialog(TransactionType.DEPOSIT, null, null, this::showDashboard));
        withdraw.setOnAction(event -> showTransactionDialog(TransactionType.WITHDRAWAL, null, null, this::showDashboard));
        transfer.setOnAction(event -> showTransactionDialog(TransactionType.TRANSFER, null, null, this::showDashboard));
        createAccount.setOnAction(event -> showCreateAccount());
        loadDemoData.setOnAction(event -> {
            com.corebanking.config.DemoDataSeeder.seed(bankService, accountService, transactionService);
            showDashboard();
            AlertUtil.success("Demo Data Loaded", "Sample bank, accounts, and transactions were created.");
        });
        quickActions.getStyleClass().add("quick-actions");
        VBox activity = panel(recentTitle, table);
        VBox.setVgrow(activity, Priority.ALWAYS);
        page.getChildren().addAll(cards, quickActions, activity);
        show(page);
    }

    private Node statCard(String label, String value, String hint) {
        VBox card = new VBox(8);
        card.getStyleClass().add("stat-card");
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("stat-label");
        Label valueNode = new Label(value);
        valueNode.getStyleClass().add("stat-value");
        valueNode.setWrapText(true);
        Label hintNode = new Label(hint);
        hintNode.getStyleClass().add("stat-hint");
        card.getChildren().addAll(labelNode, valueNode, hintNode);
        HBox.setHgrow(card, Priority.ALWAYS);
        return card;
    }

    private void showAccounts() {
        setSection("Accounts", "Search, review, and manage customer account lifecycles");
        VBox page = page();
        List<Account> summaryAccounts = accountService.listAccounts();
        HBox summary = new HBox(14,
            statCard("TOTAL ACCOUNTS", String.valueOf(summaryAccounts.size()), "Customer accounts"),
            statCard("ACTIVE", String.valueOf(summaryAccounts.stream()
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE).count()), "Ready to transact"),
            statCard("TOTAL BALANCE", FormatUtil.money(summaryAccounts.stream()
                .map(Account::getBalance).reduce(BigDecimal.ZERO, BigDecimal::add)), "Across customer accounts"),
            statCard("VISIBLE BANKS", String.valueOf(bankService.listBanks().size()), "Available for accounts"));
        summary.getStyleClass().add("stat-row");
        TextField search = new TextField();
        search.setPromptText("Search account ID or customer name");
        ComboBox<AccountStatus> statusFilter = new ComboBox<>(FXCollections.observableArrayList(AccountStatus.values()));
        statusFilter.setPromptText("All statuses");
        ComboBox<AccountType> typeFilter = new ComboBox<>(FXCollections.observableArrayList(AccountType.values()));
        typeFilter.setPromptText("All account types");
        Button refresh = secondaryButton("Refresh");
        Button create = primaryButton("Create Account");
        HBox filters = new HBox(10, search, statusFilter, typeFilter, refresh, create);
        filters.getStyleClass().add("toolbar");
        HBox.setHgrow(search, Priority.ALWAYS);

        TableView<Account> table = accountTable();
        Runnable reload = () -> {
            List<Account> accounts = accountService.listAccounts();
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            AccountStatus selectedStatus = statusFilter.getValue();
            AccountType selectedType = typeFilter.getValue();
            table.setItems(FXCollections.observableArrayList(accounts.stream()
                    .filter(account -> query.isEmpty()
                            || account.getAccountId().toLowerCase().contains(query)
                            || account.getCustomerName().toLowerCase().contains(query))
                    .filter(account -> selectedStatus == null || account.getStatus() == selectedStatus)
                    .filter(account -> selectedType == null || account.getAccountType() == selectedType)
                    .toList()));
            table.setPlaceholder(emptyState("No accounts found."));
        };
        search.textProperty().addListener((observable, oldValue, newValue) -> reload.run());
        statusFilter.valueProperty().addListener((observable, oldValue, newValue) -> reload.run());
        typeFilter.valueProperty().addListener((observable, oldValue, newValue) -> reload.run());
        refresh.setOnAction(event -> reload.run());
        create.setOnAction(event -> showCreateAccount());
        table.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) {
                showAccountDetails(table.getSelectionModel().getSelectedItem());
            }
        });
        reload.run();
        VBox panel = panel(filters, table);
        VBox.setVgrow(panel, Priority.ALWAYS);
        page.getChildren().addAll(summary, panel);
        show(page);
    }

    private TableView<Account> accountTable() {
        TableView<Account> table = new TableView<>();
        table.getStyleClass().add("data-table");
        table.getColumns().addAll(
                column("Account ID", Account::getAccountId, 130),
                column("Customer", Account::getCustomerName, 180),
                column("Type", account -> account.getAccountType().name(), 100),
                moneyColumn("Balance", Account::getBalance, 150),
                column("Status", account -> account.getStatus().name(), 110),
                column("Bank", account -> bankName(account.getBankId()), 150),
                actionColumn());
        return table;
    }

    private TableColumn<Account, String> actionColumn() {
        TableColumn<Account, String> action = new TableColumn<>("Action");
        action.setPrefWidth(105);
        action.setCellFactory(column -> new TableCell<>() {
            private final Button view = secondaryButton("View");

            {
                view.setOnAction(event -> {
                    Account account = (Account) getTableRow().getItem();
                    if (account != null) {
                        showAccountDetails(account);
                    }
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : view);
            }
        });
        return action;
    }

    private void showCreateAccount() {
        setSection("Create Account", "Open a new active customer account");
        VBox page = page();
        GridPane form = formGrid();
        TextField accountId = input("ACC100001");
        TextField customer = input("Customer name");
        ComboBox<AccountType> type = new ComboBox<>(FXCollections.observableArrayList(AccountType.values()));
        type.setPromptText("Select account type");
        TextField balance = input("0.00");
        ComboBox<Bank> bank = new ComboBox<>(FXCollections.observableArrayList(bankRepository.findAll()));
        bank.setPromptText("Select bank");
        bank.setCellFactory(list -> bankCell());
        bank.setButtonCell(bankCell());
        Button createBank = secondaryButton("Create Bank");
        createBank.setOnAction(event -> showCreateBank(() -> bank.setItems(
            FXCollections.observableArrayList(bankService.listBanks()))));
        form.addRow(0, formLabel("Account ID"), accountId);
        form.addRow(1, formLabel("Customer name"), customer);
        form.addRow(2, formLabel("Account type"), type);
        form.addRow(3, formLabel("Initial balance"), balance);
        HBox bankRow = new HBox(10, bank, createBank);
        HBox.setHgrow(bank, Priority.ALWAYS);
        form.addRow(4, formLabel("Bank"), bankRow);
        Button save = primaryButton("Create Account");
        Button clear = secondaryButton("Clear");
        clear.setOnAction(event -> { accountId.clear(); customer.clear(); type.setValue(null); balance.clear(); bank.setValue(null); });
        save.setOnAction(event -> {
            try {
                BigDecimal initialBalance = new BigDecimal(balance.getText().trim());
                if (bank.getValue() == null) {
                    throw new IllegalArgumentException("Select a bank.");
                }
                accountService.createAccount(accountId.getText(), customer.getText(), bank.getValue().getBankId(),
                        type.getValue(), initialBalance);
                AlertUtil.success("Account created", "Account " + accountId.getText().trim() + " is now active.");
                showAccounts();
            } catch (Exception exception) {
                AlertUtil.error(exception);
            }
        });
        HBox actions = new HBox(10, save, clear);
        VBox card = panel(sectionLabel("Account information"), form, actions);
        page.getChildren().add(card);
        show(page);
    }

    private void showCreateBank(Runnable afterCreate) {
        DialogWindow dialog = new DialogWindow("Create bank");
        GridPane form = formGrid();
        TextField code = input("BANK-A");
        TextField name = input("Bank name");
        form.addRow(0, formLabel("Bank code"), code);
        form.addRow(1, formLabel("Bank name"), name);
        Button save = primaryButton("Create Bank");
        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(event -> dialog.close());
        save.setOnAction(event -> {
            save.setDisable(true);
            try {
                bankService.createBank(code.getText(), name.getText());
                dialog.close();
                afterCreate.run();
                AlertUtil.success("Bank created", "The bank is ready for account creation.");
            } catch (Exception exception) {
                AlertUtil.error(exception);
                save.setDisable(false);
            }
        });
        dialog.setContent(panel(form, new HBox(10, save, cancel)));
        dialog.show();
    }

    private ListCell<Bank> bankCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Bank item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getBankCode() + "  |  " + item.getBankName());
            }
        };
    }

    private void showAccountDetails(Account account) {
        setSection("Account Details", "Lifecycle controls and account activity");
        Account current = accountService.findAccount(account.getAccountId());
        VBox page = page();
        Button back = secondaryButton("Back to Accounts");
        back.setOnAction(event -> showAccounts());
        Label identity = new Label(current.getCustomerName() + "  |  " + current.getAccountId());
        identity.getStyleClass().add("detail-identity");
        Label state = new Label("● " + current.getStatus().name());
        state.getStyleClass().add(current.getStatus() == AccountStatus.ACTIVE ? "status-active" : "status-muted");
        HBox identityRow = new HBox(14, back, identity, state);
        identityRow.setAlignment(Pos.CENTER_LEFT);
        Label balanceTitle = new Label("Current Balance");
        balanceTitle.getStyleClass().add("balance-caption");
        Label balance = new Label(FormatUtil.money(current.getBalance()));
        balance.getStyleClass().add("balance-hero");
        VBox balancePanel = panel(balanceTitle, balance);
        GridPane details = formGrid();
        details.addRow(0, detailLabel("Account ID"), detailValue(current.getAccountId()));
        details.addRow(1, detailLabel("Customer"), detailValue(current.getCustomerName()));
        details.addRow(2, detailLabel("Account type"), detailValue(current.getAccountType().name()));
        details.addRow(3, detailLabel("Bank"), detailValue(bankName(current.getBankId())));
        details.addRow(4, detailLabel("Balance"), detailValue(FormatUtil.money(current.getBalance())));
        details.addRow(5, detailLabel("Status"), detailValue(current.getStatus().name()));

        HBox actions = new HBox(10);
        Button deposit = primaryButton("Deposit");
            Button withdraw = secondaryButton("Withdraw");
            Button transfer = secondaryButton("Transfer");
        Button activate = secondaryButton("Activate");
        Button deactivate = secondaryButton("Deactivate");
        Button close = dangerButton("Close Account");
            deposit.setOnAction(event -> showTransactionDialog(TransactionType.DEPOSIT, current.getAccountId(),
                null, () -> showAccountDetails(accountService.findAccount(current.getAccountId()))));
            withdraw.setOnAction(event -> showTransactionDialog(TransactionType.WITHDRAWAL, current.getAccountId(),
                null, () -> showAccountDetails(accountService.findAccount(current.getAccountId()))));
            transfer.setOnAction(event -> showTransactionDialog(TransactionType.TRANSFER, current.getAccountId(),
                null, () -> showAccountDetails(accountService.findAccount(current.getAccountId()))));
        activate.setOnAction(event -> changeStatus(() -> accountService.activateAccount(current.getAccountId())));
        deactivate.setOnAction(event -> changeStatus(() -> accountService.deactivateAccount(current.getAccountId())));
        close.setOnAction(event -> {
            if (AlertUtil.confirm("Close account", "Close " + current.getAccountId() + "? This requires a zero balance.")) {
                changeStatus(() -> accountService.closeAccount(current.getAccountId()));
            }
        });
        boolean active = current.getStatus() == AccountStatus.ACTIVE;
        boolean inactive = current.getStatus() == AccountStatus.INACTIVE;
        deposit.setDisable(!active); withdraw.setDisable(!active); transfer.setDisable(!active);
        activate.setDisable(!inactive); deactivate.setDisable(!active); close.setDisable(!active && !inactive);
        actions.getChildren().addAll(deposit, withdraw, transfer, activate, deactivate, close);
        VBox card = panel(sectionLabel("Account information"), details);
        VBox moneyActions = panel(sectionLabel("Money operations"), actions);
        List<Transaction> recent = transactionRepository.findByAccountId(current.getAccountId()).stream().limit(6).toList();
        TableView<Transaction> recentTable = transactionTable(recent);
        recentTable.setPlaceholder(emptyState("No account activity yet."));
        VBox activity = panel(sectionLabel("Recent account activity"), recentTable);
        page.getChildren().addAll(identityRow, balancePanel, card, moneyActions, activity);
        show(page);
    }

    private void changeStatus(java.util.function.Supplier<Account> operation) {
        try {
            operation.get();
            showAccounts();
        } catch (Exception exception) {
            AlertUtil.error(exception);
        }
    }

    private void showTransactions() {
        setSection("Transactions", "Searchable history of persisted financial activity");
        VBox page = page();
        TextField search = new TextField();
        search.setPromptText("Search transaction ID, account, or description");
        ComboBox<TransactionType> type = new ComboBox<>(FXCollections.observableArrayList(TransactionType.values()));
        type.setPromptText("All types");
        ComboBox<TransactionStatus> status = new ComboBox<>(FXCollections.observableArrayList(TransactionStatus.values()));
        status.setPromptText("All statuses");
        Button refresh = secondaryButton("Refresh");
        HBox toolbar = new HBox(10, search, type, status, refresh);
        toolbar.getStyleClass().add("toolbar");
        HBox.setHgrow(search, Priority.ALWAYS);
        TableView<Transaction> table = transactionTable(List.of());
        Runnable reload = () -> {
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            List<Transaction> filtered = transactionRepository.findAll().stream()
                    .filter(transaction -> query.isEmpty() || transaction.getTransactionId().toLowerCase().contains(query)
                            || String.valueOf(transaction.getFromAccount()).toLowerCase().contains(query)
                            || String.valueOf(transaction.getToAccount()).toLowerCase().contains(query)
                            || String.valueOf(transaction.getDescription()).toLowerCase().contains(query))
                    .filter(transaction -> type.getValue() == null || transaction.getTransactionType() == type.getValue())
                    .filter(transaction -> status.getValue() == null || transaction.getStatus() == status.getValue())
                    .toList();
            table.setItems(FXCollections.observableArrayList(filtered));
            table.setPlaceholder(emptyState("No transactions yet."));
        };
        search.textProperty().addListener((observable, oldValue, newValue) -> reload.run());
        type.valueProperty().addListener((observable, oldValue, newValue) -> reload.run());
        status.valueProperty().addListener((observable, oldValue, newValue) -> reload.run());
        refresh.setOnAction(event -> reload.run());
        table.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) showReceipt(table.getSelectionModel().getSelectedItem(), null); });
        reload.run();
        VBox panel = panel(toolbar, table);
        VBox.setVgrow(panel, Priority.ALWAYS);
        page.getChildren().add(panel);
        show(page);
    }

    private void showNewTransaction() {
        DialogWindow dialog = new DialogWindow("New transaction");
        Label heading = sectionLabel("Choose an operation");
        Label hint = new Label("All operations use the existing atomic transaction and ledger services.");
        hint.getStyleClass().add("section-hint");
        Button deposit = primaryButton("+  Deposit Money");
        Button withdraw = secondaryButton("-  Withdraw Money");
        Button transfer = secondaryButton("⇄  Transfer Money");
        deposit.setMaxWidth(Double.MAX_VALUE);
        withdraw.setMaxWidth(Double.MAX_VALUE);
        transfer.setMaxWidth(Double.MAX_VALUE);
        deposit.setOnAction(event -> { dialog.close(); showTransactionDialog(TransactionType.DEPOSIT, null, null, this::showDashboard); });
        withdraw.setOnAction(event -> { dialog.close(); showTransactionDialog(TransactionType.WITHDRAWAL, null, null, this::showDashboard); });
        transfer.setOnAction(event -> { dialog.close(); showTransactionDialog(TransactionType.TRANSFER, null, null, this::showDashboard); });
        dialog.setContent(new VBox(14, heading, hint, deposit, withdraw, transfer));
        dialog.show();
    }

    private TableView<Transaction> transactionTable(List<Transaction> transactions) {
        TableView<Transaction> table = new TableView<>(FXCollections.observableArrayList(transactions));
        table.getStyleClass().add("data-table");
        table.getColumns().addAll(
                column("Transaction ID", Transaction::getTransactionId, 240),
                column("Type", transaction -> transaction.getTransactionType().name(), 110),
                column("From", transaction -> valueOrDash(transaction.getFromAccount()), 130),
                column("To", transaction -> valueOrDash(transaction.getToAccount()), 130),
                moneyColumn("Amount", Transaction::getAmount, 130),
                column("Status", transaction -> transaction.getStatus().name(), 110),
                column("Timestamp", transaction -> FormatUtil.dateTime(transaction.getCreatedAt()), 180));
        return table;
    }

    private void showLedger() {
        setSection("Ledger", "Double-entry records with debit and credit verification");
        VBox page = page();
        TextField transactionFilter = new TextField();
        transactionFilter.setPromptText("Filter by transaction ID");
        TextField accountFilter = new TextField();
        accountFilter.setPromptText("Filter by account");
        ComboBox<LedgerEntryType> type = new ComboBox<>(FXCollections.observableArrayList(LedgerEntryType.values()));
        type.setPromptText("All entries");
        Button refresh = secondaryButton("Refresh");
        HBox toolbar = new HBox(10, transactionFilter, accountFilter, type, refresh);
        toolbar.getStyleClass().add("toolbar");
        HBox.setHgrow(transactionFilter, Priority.ALWAYS);
        TableView<LedgerEntry> table = ledgerTable();
        Label totals = new Label();
        totals.getStyleClass().add("ledger-total");
        Runnable reload = () -> {
            String transactionQuery = transactionFilter.getText() == null ? "" : transactionFilter.getText().trim().toLowerCase();
            String accountQuery = accountFilter.getText() == null ? "" : accountFilter.getText().trim().toLowerCase();
            List<LedgerEntry> entries = ledgerRepository.findAll().stream()
                    .filter(entry -> transactionQuery.isEmpty() || entry.getTransactionId().toLowerCase().contains(transactionQuery))
                    .filter(entry -> accountQuery.isEmpty() || entry.getAccountId().toLowerCase().contains(accountQuery))
                    .filter(entry -> type.getValue() == null || entry.getEntryType() == type.getValue()).toList();
            table.setItems(FXCollections.observableArrayList(entries));
            table.setPlaceholder(emptyState("No ledger entries available."));
            BigDecimal debits = entries.stream().filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credits = entries.stream().filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            totals.setText("Total debits  " + FormatUtil.money(debits) + "    |    Total credits  " + FormatUtil.money(credits)
                    + "    |    " + (debits.compareTo(credits) == 0 ? "BALANCED" : "CHECK REQUIRED"));
        };
        transactionFilter.textProperty().addListener((observable, oldValue, newValue) -> reload.run());
        accountFilter.textProperty().addListener((observable, oldValue, newValue) -> reload.run());
        type.valueProperty().addListener((observable, oldValue, newValue) -> reload.run());
        refresh.setOnAction(event -> reload.run());
        reload.run();
        VBox panel = panel(toolbar, table, totals);
        VBox.setVgrow(panel, Priority.ALWAYS);
        page.getChildren().add(panel);
        show(page);
    }

    private TableView<LedgerEntry> ledgerTable() {
        TableView<LedgerEntry> table = new TableView<>();
        table.getStyleClass().add("data-table");
        TableColumn<LedgerEntry, String> debit = moneyColumn("Debit", entry -> entry.getEntryType() == LedgerEntryType.DEBIT ? entry.getAmount() : null, 120);
        TableColumn<LedgerEntry, String> credit = moneyColumn("Credit", entry -> entry.getEntryType() == LedgerEntryType.CREDIT ? entry.getAmount() : null, 120);
        table.getColumns().addAll(column("Ledger ID", entry -> String.valueOf(entry.getLedgerEntryId()), 90), column("Transaction ID", LedgerEntry::getTransactionId, 230), column("Account", LedgerEntry::getAccountId, 130), column("Entry", entry -> entry.getEntryType().name(), 90), debit, credit, column("Description", entry -> valueOrDash(entry.getDescription()), 180), column("Timestamp", entry -> FormatUtil.dateTime(entry.getCreatedAt()), 170));
        return table;
    }

    private void showTransactionDialog(TransactionType transactionType, String presetAccount,
                                      String presetDestination, Runnable afterReceiptClosed) {
        DialogWindow dialog = new DialogWindow(transactionType == TransactionType.TRANSFER ? "Transfer funds" : transactionType == TransactionType.DEPOSIT ? "Deposit funds" : "Withdraw funds");
        GridPane form = formGrid();
        ComboBox<Account> from = accountPicker();
        ComboBox<Account> to = accountPicker();
        TextField amount = input("0.00");
        TextArea description = new TextArea();
        description.setPromptText("Description (optional)");
        description.setPrefRowCount(3);
        if (presetAccount != null) {
            if (transactionType == TransactionType.DEPOSIT) {
                to.setValue(findAccount(presetAccount));
            } else {
                from.setValue(findAccount(presetAccount));
            }
        }
        form.addRow(0, formLabel(transactionType == TransactionType.DEPOSIT ? "Account" : "From account"), transactionType == TransactionType.DEPOSIT ? to : from);
        if (transactionType == TransactionType.TRANSFER) form.addRow(1, formLabel("To account"), to);
        form.addRow(transactionType == TransactionType.TRANSFER ? 2 : 1, formLabel("Amount"), amount);
        form.addRow(transactionType == TransactionType.TRANSFER ? 3 : 2, formLabel("Description"), description);
        Label preview = new Label();
        preview.getStyleClass().add("calculation-preview");
        Runnable updatePreview = () -> {
            try {
                BigDecimal entered = new BigDecimal(amount.getText().trim());
                Account selected = transactionType == TransactionType.DEPOSIT ? to.getValue() : from.getValue();
                if (selected != null) preview.setText("Current balance  " + FormatUtil.money(selected.getBalance()) + "    |    Amount  " + FormatUtil.money(entered));
            } catch (Exception ignored) { preview.setText(""); }
        };
        amount.textProperty().addListener((observable, oldValue, newValue) -> updatePreview.run());
        from.valueProperty().addListener((observable, oldValue, newValue) -> updatePreview.run());
        to.valueProperty().addListener((observable, oldValue, newValue) -> updatePreview.run());
        Button submit = primaryButton(transactionType == TransactionType.DEPOSIT ? "Deposit" : transactionType == TransactionType.WITHDRAWAL ? "Withdraw" : "Transfer");
        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(event -> dialog.close());
        submit.setOnAction(event -> {
            submit.setDisable(true);
            try {
                BigDecimal value = new BigDecimal(amount.getText().trim());
                Transaction result;
                if (transactionType == TransactionType.DEPOSIT) result = transactionService.deposit(to.getValue().getAccountId(), value, description.getText());
                else if (transactionType == TransactionType.WITHDRAWAL) result = transactionService.withdraw(from.getValue().getAccountId(), value, description.getText());
                else result = transactionService.transfer(from.getValue().getAccountId(), to.getValue().getAccountId(), value, description.getText());
                dialog.close();
                showReceipt(result, afterReceiptClosed);
            } catch (Exception exception) {
                AlertUtil.error(exception);
                submit.setDisable(false);
            }
        });
        VBox formContainer = new VBox(14, form, preview);
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(formContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        scrollPane.getStyleClass().add("main-scroll-pane");
        scrollPane.setPrefViewportHeight(300);
        dialog.setContent(panel(scrollPane, new HBox(10, submit, cancel)));
        dialog.show();
    }

    private ComboBox<Account> accountPicker() {
        ComboBox<Account> picker = new ComboBox<>(FXCollections.observableArrayList(accountService.listAccounts()));
        picker.setPromptText("Select account");
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setCellFactory(list -> accountCell());
        picker.setButtonCell(accountCell());
        return picker;
    }

    private ListCell<Account> accountCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getAccountId() + "  |  " + item.getCustomerName());
            }
        };
    }

    private void showReceipt(Transaction transaction, Runnable afterClosed) {
        DialogWindow dialog = new DialogWindow("Transaction receipt");
        VBox receipt = new VBox(10);
        receipt.getStyleClass().add("receipt");
        Label title = new Label("TRANSACTION RECEIPT");
        title.getStyleClass().add("receipt-title");
        receipt.getChildren().add(title);
        receipt.getChildren().addAll(receiptRow("Transaction ID", transaction.getTransactionId()), receiptRow("Date / Time", FormatUtil.dateTime(transaction.getCreatedAt())), receiptRow("Type", transaction.getTransactionType().name()), receiptRow("From account", valueOrDash(transaction.getFromAccount())), receiptRow("To account", valueOrDash(transaction.getToAccount())), receiptRow("Amount", FormatUtil.money(transaction.getAmount())), receiptRow("Status", transaction.getStatus().name()), receiptRow("Description", valueOrDash(transaction.getDescription())));
        Label success = new Label("SUCCESS");
        success.getStyleClass().add("receipt-success");
        Button close = primaryButton("Close");
        close.setOnAction(event -> {
            dialog.close();
            if (afterClosed != null) {
                afterClosed.run();
            }
        });
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(receipt);
        scrollPane.setFitToWidth(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        scrollPane.getStyleClass().add("main-scroll-pane");
        scrollPane.setPrefViewportHeight(350);
        dialog.setContent(new VBox(14, scrollPane, success, close));
        dialog.show();
    }

    private HBox receiptRow(String label, String value) {
        Label key = new Label(label);
        key.getStyleClass().add("receipt-key");
        Label content = new Label(value);
        content.getStyleClass().add("receipt-value");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        return new HBox(12, key, spacer, content);
    }

    private void setSection(String title, String hint) {
        sectionTitle.setText(title);
        sectionHint.setText(hint);
    }

    private void show(Node node) {
        javafx.scene.control.ScrollPane scrollPane = new javafx.scene.control.ScrollPane(node);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.setStyle("-fx-background-color: transparent; -fx-background-insets: 0; -fx-padding: 0;");
        scrollPane.getStyleClass().add("main-scroll-pane");
        content.getChildren().setAll(scrollPane);
    }

    private VBox page() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        return page;
    }

    private VBox panel(Node... nodes) {
        VBox panel = new VBox(14, nodes);
        panel.getStyleClass().add("panel");
        return panel;
    }

    private Label sectionLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("panel-title");
        return label;
    }

    private Label formLabel(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("form-label");
        return label;
    }

    private Label detailLabel(String text) {
        Label label = formLabel(text);
        label.setMinWidth(130);
        return label;
    }

    private Label detailValue(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("detail-value");
        return label;
    }

    private TextField input(String prompt) {
        TextField field = new TextField();
        field.setPromptText(prompt);
        field.setMaxWidth(Double.MAX_VALUE);
        return field;
    }

    private GridPane formGrid() {
        GridPane grid = new GridPane();
        grid.setHgap(18);
        grid.setVgap(14);
        grid.getColumnConstraints().addAll(new ColumnConstraints(140), new ColumnConstraints(360));
        return grid;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("primary-button");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().add("danger-button");
        return button;
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, String> moneyColumn(String title, Function<T, BigDecimal> value, double width) {
        TableColumn<T, String> col = column(title, row -> FormatUtil.money(value.apply(row)), width);
        col.getStyleClass().add("money-column");
        return col;
    }

    private Label emptyState(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("empty-state");
        label.setWrapText(true);
        label.setMaxWidth(Double.MAX_VALUE);
        return label;
    }

    private String bankName(Long bankId) {
        return bankRepository.findById(bankId).map(bank -> bank.getBankCode() + " / " + bank.getBankName()).orElse("Unknown bank");
    }

    private Account findAccount(String accountId) {
        return accountService.findAccount(accountId);
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private static final class DialogWindow {
        private final Stage stage = new Stage();
        private final VBox root = new VBox(14);

        private DialogWindow(String title) {
            root.getStyleClass().add("dialog-content");
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(title);
            stage.setMinWidth(560);
        }

        private void setContent(Node node) {
            root.getChildren().setAll(node);
            Scene scene = new Scene(root);
            scene.getStylesheets().add(AppShell.class.getResource("/css/application.css").toExternalForm());
            stage.setScene(scene);
        }

        private void show() {
            stage.showAndWait();
        }

        private void close() {
            stage.close();
        }
    }
}
