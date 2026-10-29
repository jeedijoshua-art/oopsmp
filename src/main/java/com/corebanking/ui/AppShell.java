package com.corebanking.ui;

import com.corebanking.enums.AccountStatus;
import com.corebanking.enums.AccountType;
import com.corebanking.enums.LedgerEntryType;
import com.corebanking.enums.TransactionStatus;
import com.corebanking.enums.TransactionType;
import com.corebanking.model.Account;
import com.corebanking.model.AccountReconciliationRecord;
import com.corebanking.model.Bank;
import com.corebanking.model.LedgerEntry;
import com.corebanking.model.ReconciliationReport;
import com.corebanking.model.Transaction;
import com.corebanking.repository.AccountRepository;
import com.corebanking.repository.BankRepository;
import com.corebanking.repository.LedgerRepository;
import com.corebanking.repository.TransactionRepository;
import com.corebanking.service.AccountService;
import com.corebanking.service.BankService;
import com.corebanking.service.LedgerService;
import com.corebanking.service.LedgerServiceImpl;
import com.corebanking.service.TransactionService;
import com.corebanking.ui.util.AlertUtil;
import com.corebanking.ui.util.FormatUtil;
import com.corebanking.util.CsvExporter;
import javafx.beans.property.SimpleObjectProperty;
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
import javafx.scene.control.Hyperlink;
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
import javafx.scene.shape.SVGPath;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.File;
import java.util.function.Supplier;

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
    private final LedgerService ledgerService;
    private final com.corebanking.service.DatabaseManagementService dbmService;
    private final BorderPane root = new BorderPane();
    private final StackPane content = new StackPane();
    private final Label sectionTitle = new Label();
    private final Label sectionHint = new Label();
    private final ToggleGroup navigationGroup = new ToggleGroup();

    public AppShell(Connection connection, AccountService accountService, BankService bankService,
                    TransactionService transactionService, com.corebanking.service.DatabaseManagementService dbmService) {
        this.connection = Objects.requireNonNull(connection);
        this.accountService = Objects.requireNonNull(accountService);
        this.bankService = Objects.requireNonNull(bankService);
        this.transactionService = Objects.requireNonNull(transactionService);
        this.bankRepository = new BankRepository(connection);
        this.transactionRepository = new TransactionRepository(connection);
        this.ledgerRepository = new LedgerRepository(connection);
        this.ledgerService = new LedgerServiceImpl(this.ledgerRepository, this.transactionRepository);
        this.dbmService = Objects.requireNonNull(dbmService);
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
        sidebar.setPrefWidth(248);

        Label brand = new Label("VAULTLEDGER");
        brand.getStyleClass().add("brand");
        Label brandSub = new Label("Core Banking Ledger System");
        brandSub.getStyleClass().add("brand-subtitle");
        VBox brandBox = new VBox(4, brand, brandSub);
        brandBox.getStyleClass().add("brand-box");

        VBox nav = new VBox(6);
        nav.getStyleClass().add("nav-list");
        nav.getChildren().addAll(
                navButton("⊞  Dashboard", "Overview", this::showDashboard),
                navButton("👥  Accounts", "Customer accounts", this::showAccounts),
                navButton("⇄  Transactions", "Financial activity", this::showTransactions),
                navButton("📒  Ledger", "Double-entry records", this::showLedger),
                navButton("✚  Create Account", "Open a new account", this::showCreateAccount));

        Region spacer = new Region();
        VBox.setVgrow(spacer, Priority.ALWAYS);
        
        Label footerSim = new Label("ACADEMIC SIMULATION");
        footerSim.getStyleClass().add("sidebar-footer-title");
        Label footerDb = new Label("SQLite local workspace");
        footerDb.getStyleClass().add("sidebar-footer-subtitle");
        Node githubLink = buildGitHubLink();
        VBox footerBox = new VBox(5, footerSim, footerDb, githubLink);
        footerBox.getStyleClass().add("sidebar-footer");

        sidebar.getChildren().addAll(brandBox, new Separator(), nav, spacer, footerBox);
        return sidebar;
    }

    private Node buildGitHubLink() {
        SVGPath icon = new SVGPath();
        icon.setContent("M8 0C3.58 0 0 3.58 0 8c0 3.54 2.29 6.53 5.47 7.59.4.07.55-.17.55-.38 0-.19-.01-.82-.01-1.49-2.01.37-2.53-.49-2.69-.94-.09-.23-.48-.94-.82-1.13-.28-.15-.68-.52-.01-.53.63-.01 1.08.58 1.23.82.72 1.21 1.87.87 2.33.66.07-.52.28-.87.51-1.07-1.78-.2-3.64-.89-3.64-3.95 0-.87.31-1.59.82-2.15-.08-.2-.36-1.02.08-2.12 0 0 .67-.21 2.2.82.64-.18 1.32-.27 2-.27.68 0 1.36.09 2 .27 1.53-1.04 2.2-.82 2.2-.82.44 1.1.16 1.92.08 2.12.51.56.82 1.27.82 2.15 0 3.07-1.87 3.75-3.65 3.95.29.25.54.73.54 1.48 0 1.07-.01 1.93-.01 2.2 0 .21.15.46.55.38A8.012 8.012 0 0 0 16 8c0-4.42-3.58-8-8-8z");
        icon.getStyleClass().add("github-icon");

        Hyperlink link = new Hyperlink("View on GitHub", icon);
        link.getStyleClass().add("github-link");
        link.setAccessibleText("View project repository on GitHub");
        link.setFocusTraversable(true);
        link.setOnAction(event -> openBrowser("https://github.com/jeedijoshua-art/oopsmp"));

        HBox container = new HBox(link);
        container.getStyleClass().add("github-link-container");
        container.setAlignment(Pos.CENTER_LEFT);
        return container;
    }

    private void openBrowser(String url) {
        try {
            if (java.awt.Desktop.isDesktopSupported() && java.awt.Desktop.getDesktop().isSupported(java.awt.Desktop.Action.BROWSE)) {
                java.awt.Desktop.getDesktop().browse(new java.net.URI(url));
            } else {
                String os = System.getProperty("os.name", "").toLowerCase();
                ProcessBuilder pb;
                if (os.contains("mac")) {
                    pb = new ProcessBuilder("open", url);
                } else if (os.contains("win")) {
                    pb = new ProcessBuilder("rundll32", "url.dll,FileProtocolHandler", url);
                } else {
                    pb = new ProcessBuilder("xdg-open", url);
                }
                pb.start();
            }
        } catch (Exception ex) {
            AlertUtil.error(new RuntimeException("Unable to open repository link in browser:\n" + url, ex));
        }
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
        Button button = new Button(text);
        button.getStyleClass().addAll("button", "nav-action-button");
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
        VBox titles = new VBox(4, sectionTitle, sectionHint);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = new Label("● LOCAL DATABASE  |  ONLINE");
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
        HBox cards = new HBox(16,
                statCard("TOTAL ACCOUNTS", String.valueOf(accounts.size()), "Customer accounts"),
                statCard("ACTIVE ACCOUNTS", String.valueOf(accounts.stream()
                        .filter(account -> account.getStatus() == AccountStatus.ACTIVE).count()), "Ready to transact"),
                statCard("TRANSACTIONS", String.valueOf(transactions.size()), "All recorded activity"),
                statCard("CUSTOMER BALANCES", FormatUtil.money(totalBalances), "Across all accounts"));
        cards.getStyleClass().add("stat-row");
        HBox.setHgrow(cards.getChildren().get(3), Priority.ALWAYS);

        // Quick Actions with clean visual separation between routine ops and administrative tasks
        VBox quickActionsBox = new VBox(14);
        quickActionsBox.getStyleClass().add("quick-actions-panel");
        
        Label qaTitle = sectionLabel("Quick Actions");
        
        FlowPane routineOps = new FlowPane(10, 10);
        routineOps.setAlignment(Pos.CENTER_LEFT);
        Button deposit = primaryButton("+ Deposit");
        Button withdraw = secondaryButton("- Withdraw");
        Button transfer = secondaryButton("⇄ Transfer");
        Button createAccount = secondaryButton("+ Create Account");
        Button manageAccounts = secondaryButton("Manage Accounts");
        routineOps.getChildren().addAll(deposit, withdraw, transfer, createAccount, manageAccounts);

        Separator qaDivider = new Separator();
        qaDivider.getStyleClass().add("quick-actions-divider");

        FlowPane workspaceOps = new FlowPane(10, 10);
        workspaceOps.setAlignment(Pos.CENTER_LEFT);
        Label adminLabel = new Label("WORKSPACE & DEMO DATA:");
        adminLabel.getStyleClass().add("quick-actions-group-label");
        Button loadDemoData = secondaryButton("Load Demo Data");
        Button resetDemoData = dangerButton("Reset Demo Data");
        Button resetEntireDB = dangerButton("Reset Entire Local Workspace");
        workspaceOps.getChildren().addAll(adminLabel, loadDemoData, resetDemoData, resetEntireDB);

        quickActionsBox.getChildren().addAll(qaTitle, routineOps, qaDivider, workspaceOps);

        deposit.setOnAction(event -> showTransactionDialog(TransactionType.DEPOSIT, null, null, this::showDashboard));
        withdraw.setOnAction(event -> showTransactionDialog(TransactionType.WITHDRAWAL, null, null, this::showDashboard));
        transfer.setOnAction(event -> showTransactionDialog(TransactionType.TRANSFER, null, null, this::showDashboard));
        createAccount.setOnAction(event -> showCreateAccount());
        manageAccounts.setOnAction(event -> showAccounts());
        loadDemoData.setOnAction(event -> {
            com.corebanking.config.DemoDataSeeder.seed(connection, bankService, accountService, transactionService);
            showDashboard();
            AlertUtil.success("Demo Data Loaded", "Sample bank, accounts, and transactions were created.");
        });
        resetDemoData.setOnAction(event -> showResetDemoData());
        resetEntireDB.setOnAction(event -> showResetEntireDatabase());

        Label recentTitle = sectionLabel("Recent Transactions");
        TableView<Transaction> table = transactionTable(transactions.stream().limit(8).toList());
        
        Button startDeposit = primaryButton("+ Make a Deposit");
        startDeposit.setOnAction(e -> showTransactionDialog(TransactionType.DEPOSIT, null, null, this::showDashboard));
        table.setPlaceholder(emptyState("No transactions yet", "Your deposits, withdrawals, and transfers will appear here.", startDeposit));

        VBox activity = panel(recentTitle, table);
        VBox.setVgrow(activity, Priority.ALWAYS);
        page.getChildren().addAll(cards, quickActionsBox, activity);
        show(page);
    }

    private Node statCard(String label, String value, String hint) {
        VBox card = new VBox(6);
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
        HBox summary = new HBox(16,
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
        Button create = primaryButton("+ Create Account");
        HBox filters = new HBox(12, search, statusFilter, typeFilter, refresh, create);
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
            Button createFirst = primaryButton("+ Create Account");
            createFirst.setOnAction(e -> showCreateAccount());
            table.setPlaceholder(emptyState("No accounts found", "Adjust your filters or create a new customer account.", createFirst));
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
                column("Account ID", Account::getAccountId, 150),
                column("Customer", Account::getCustomerName, 190),
                column("Type", account -> account.getAccountType().name(), 120),
                moneyColumn("Balance", Account::getBalance, 150),
                statusColumn("Status", account -> account.getStatus().name(), 120),
                column("Bank", account -> bankName(account.getBankId()), 190),
                actionColumn());
        return table;
    }

    private TableColumn<Account, String> actionColumn() {
        TableColumn<Account, String> action = new TableColumn<>("Action");
        action.setPrefWidth(110);
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
        TextField customer = input("Customer full name");
        TextField ageInput = input("Age (e.g. 25)");
        TextField emailInput = input("Email address (e.g. user@example.com)");
        TextField phoneInput = input("Phone number (e.g. +91 9876543210)");
        ComboBox<AccountType> type = new ComboBox<>(FXCollections.observableArrayList(AccountType.values()));
        type.setPromptText("Select account type");
        type.setMaxWidth(Double.MAX_VALUE);
        TextField balance = input("0.00");
        ComboBox<Bank> bank = new ComboBox<>(FXCollections.observableArrayList(bankRepository.findAll()));
        bank.setPromptText("Select bank");
        bank.setMaxWidth(Double.MAX_VALUE);
        bank.setCellFactory(list -> bankCell());
        bank.setButtonCell(bankCell());
        Button createBank = secondaryButton("+ Create Bank");
        createBank.setOnAction(event -> showCreateBank(() -> bank.setItems(
            FXCollections.observableArrayList(bankService.listBanks()))));
        form.addRow(0, formLabel("Customer Name"), customer);
        form.addRow(1, formLabel("Age"), ageInput);
        form.addRow(2, formLabel("Email Address"), emailInput);
        form.addRow(3, formLabel("Phone Number"), phoneInput);
        form.addRow(4, formLabel("Account Type"), type);
        form.addRow(5, formLabel("Initial Balance"), balance);
        HBox bankRow = new HBox(10, bank, createBank);
        HBox.setHgrow(bank, Priority.ALWAYS);
        form.addRow(6, formLabel("Bank"), bankRow);
        Button save = primaryButton("Create Account");
        Button clear = secondaryButton("Clear");
        clear.setOnAction(event -> { customer.clear(); ageInput.clear(); emailInput.clear(); phoneInput.clear(); type.setValue(null); balance.clear(); bank.setValue(null); });
        save.setOnAction(event -> {
            try {
                BigDecimal initialBalance = new BigDecimal(balance.getText().trim());
                Integer age = null;
                try {
                    age = Integer.parseInt(ageInput.getText().trim());
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Age must be a valid number.");
                }
                if (bank.getValue() == null) {
                    throw new IllegalArgumentException("Select a bank.");
                }
                Account created = accountService.createAccount(customer.getText(), age, emailInput.getText().trim(), phoneInput.getText().trim(), bank.getValue().getBankId(),
                        type.getValue(), initialBalance);
                
                AlertUtil.success("Account created", "Account " + created.getAccountId() + " has been successfully generated and activated.");
                showAccounts();
            } catch (Exception exception) {
                AlertUtil.error(exception);
            }
        });
        HBox actions = new HBox(12, save, clear);
        VBox card = panel(sectionLabel("Account Information"), form, actions);
        page.getChildren().add(card);
        show(page);
    }

    private void showCreateBank(Runnable afterCreate) {
        DialogWindow dialog = new DialogWindow("Create Bank");
        GridPane form = formGrid();
        TextField code = input("e.g. HDFC");
        TextField name = input("Bank full name");
        form.addRow(0, formLabel("Bank Code"), code);
        form.addRow(1, formLabel("Bank Name"), name);
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
        dialog.setContent(panel(sectionLabel("New Bank Details"), form, new HBox(12, save, cancel)));
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
        Button back = secondaryButton("← Back to Accounts");
        back.setOnAction(event -> showAccounts());
        Label identity = new Label(current.getCustomerName() + "  |  " + current.getAccountId());
        identity.getStyleClass().add("detail-identity");
        
        Label state = new Label(current.getStatus().name());
        state.getStyleClass().addAll("badge", current.getStatus() == AccountStatus.ACTIVE ? "badge-success" :
                current.getStatus() == AccountStatus.INACTIVE ? "badge-warning" : "badge-danger");
        
        HBox identityRow = new HBox(16, back, identity, state);
        identityRow.setAlignment(Pos.CENTER_LEFT);
        
        Label balanceTitle = new Label("Current Balance");
        balanceTitle.getStyleClass().add("balance-caption");
        Label balance = new Label(FormatUtil.money(current.getBalance()));
        balance.getStyleClass().add("balance-hero");
        VBox balancePanel = panel(balanceTitle, balance);
        
        GridPane details = formGrid();
        details.addRow(0, detailLabel("Account ID"), detailValue(current.getAccountId()));
        details.addRow(1, detailLabel("Customer"), detailValue(current.getCustomerName()));
        details.addRow(2, detailLabel("Age"), detailValue(current.getAge() != null ? String.valueOf(current.getAge()) : "-"));
        details.addRow(3, detailLabel("Email"), detailValue(valueOrDash(current.getEmail())));
        details.addRow(4, detailLabel("Phone"), detailValue(valueOrDash(current.getPhoneNumber())));
        details.addRow(5, detailLabel("Account type"), detailValue(current.getAccountType().name()));
        details.addRow(6, detailLabel("Bank"), detailValue(bankName(current.getBankId())));
        details.addRow(7, detailLabel("Balance"), detailValue(FormatUtil.money(current.getBalance())));
        details.addRow(8, detailLabel("Status"), detailValue(current.getStatus().name()));

        FlowPane actions = new FlowPane(10, 10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button deposit = primaryButton("+ Deposit");
        Button withdraw = secondaryButton("- Withdraw");
        Button transfer = secondaryButton("⇄ Transfer");
        Button activate = secondaryButton("Activate");
        Button deactivate = secondaryButton("Deactivate");
        Button close = dangerButton("Close Account");
        Button delete = dangerButton("Delete");
        
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
        delete.setOnAction(event -> {
            if (AlertUtil.confirm("Delete account", "Permanently delete " + current.getAccountId() + "? This cannot be undone.")) {
                try {
                    accountService.deleteAccount(current.getAccountId());
                    showAccounts();
                    AlertUtil.success("Deleted", "Account successfully deleted.");
                } catch (Exception e) {
                    AlertUtil.error(e);
                }
            }
        });
        boolean active = current.getStatus() == AccountStatus.ACTIVE;
        boolean inactive = current.getStatus() == AccountStatus.INACTIVE;
        boolean isClosed = current.getStatus() == AccountStatus.CLOSED;
        deposit.setDisable(!active); withdraw.setDisable(!active); transfer.setDisable(!active);
        activate.setDisable(!inactive && !isClosed); deactivate.setDisable(!active); close.setDisable(!active && !inactive);
        delete.setDisable(!isClosed);
        actions.getChildren().addAll(deposit, withdraw, transfer, activate, deactivate, close, delete);
        
        VBox card = panel(sectionLabel("Account Information"), details);
        VBox moneyActions = panel(sectionLabel("Account & Money Operations"), actions);
        List<Transaction> recent = transactionRepository.findByAccountId(current.getAccountId()).stream().limit(6).toList();
        TableView<Transaction> recentTable = transactionTable(recent);
        recentTable.setPlaceholder(emptyState("No account activity yet", "Transactions involving this account will appear here.", null));
        VBox activity = panel(sectionLabel("Recent Account Activity"), recentTable);
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
        Button exportCsv = secondaryButton("⬇ Export CSV");
        HBox toolbar = new HBox(12, search, type, status, refresh, exportCsv);
        toolbar.getStyleClass().add("toolbar");
        HBox.setHgrow(search, Priority.ALWAYS);
        
        TableView<Transaction> table = transactionTable(List.of());
        Supplier<List<Transaction>> filteredSupplier = () -> {
            String query = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            return transactionRepository.findAll().stream()
                    .filter(transaction -> query.isEmpty() || transaction.getTransactionId().toLowerCase().contains(query)
                            || String.valueOf(transaction.getFromAccount()).toLowerCase().contains(query)
                            || String.valueOf(transaction.getToAccount()).toLowerCase().contains(query)
                            || String.valueOf(transaction.getDescription()).toLowerCase().contains(query))
                    .filter(transaction -> type.getValue() == null || transaction.getTransactionType() == type.getValue())
                    .filter(transaction -> status.getValue() == null || transaction.getStatus() == status.getValue())
                    .toList();
        };

        Runnable reload = () -> {
            List<Transaction> filtered = filteredSupplier.get();
            table.setItems(FXCollections.observableArrayList(filtered));
            table.setPlaceholder(emptyState("No transactions found", "Try adjusting your search criteria or create a new transaction.", null));
        };
        search.textProperty().addListener((observable, oldValue, newValue) -> reload.run());
        type.valueProperty().addListener((observable, oldValue, newValue) -> reload.run());
        status.valueProperty().addListener((observable, oldValue, newValue) -> reload.run());
        refresh.setOnAction(event -> reload.run());
        exportCsv.setOnAction(event -> {
            List<Transaction> matching = filteredSupplier.get();
            if (matching.isEmpty()) {
                AlertUtil.info("No Records to Export", "There are no transactions matching your current search and filter criteria.");
                return;
            }
            exportTransactionsCsv(matching, exportCsv);
        });
        table.setOnMouseClicked(event -> { if (event.getClickCount() == 2 && table.getSelectionModel().getSelectedItem() != null) showReceipt(table.getSelectionModel().getSelectedItem(), null); });
        reload.run();
        VBox panel = panel(toolbar, table);
        VBox.setVgrow(panel, Priority.ALWAYS);
        page.getChildren().add(panel);
        show(page);
    }

    private void showNewTransaction() {
        DialogWindow dialog = new DialogWindow("New Transaction");
        Label heading = sectionLabel("Choose an Operation");
        Label hint = new Label("All operations use atomic transaction guarantees and double-entry ledger verification.");
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
        dialog.setContent(panel(heading, hint, new VBox(12, deposit, withdraw, transfer)));
        dialog.show();
    }

    private TableView<Transaction> transactionTable(List<Transaction> transactions) {
        TableView<Transaction> table = new TableView<>(FXCollections.observableArrayList(transactions));
        table.getStyleClass().add("data-table");
        table.getColumns().addAll(
                column("Transaction ID", Transaction::getTransactionId, 320),
                column("Type", transaction -> transaction.getTransactionType().name(), 120),
                column("From", transaction -> valueOrDash(transaction.getFromAccount()), 140),
                column("To", transaction -> valueOrDash(transaction.getToAccount()), 140),
                moneyColumn("Amount", Transaction::getAmount, 140),
                statusColumn("Status", transaction -> transaction.getStatus().name(), 120),
                column("Timestamp", transaction -> FormatUtil.dateTime(transaction.getCreatedAt()), 180));
        return table;
    }

    private void showLedger() {
        setSection("Ledger & Reconciliation", "Double-entry records with mathematical audit verification");
        VBox page = page();

        ToggleButton journalViewBtn = new ToggleButton("Double-Entry Journal");
        journalViewBtn.getStyleClass().addAll("toggle-button", "button");
        ToggleButton auditViewBtn = new ToggleButton("Integrity & Audit Reconciliation");
        auditViewBtn.getStyleClass().addAll("toggle-button", "button");
        ToggleGroup viewGroup = new ToggleGroup();
        journalViewBtn.setToggleGroup(viewGroup);
        auditViewBtn.setToggleGroup(viewGroup);
        journalViewBtn.setSelected(true);

        HBox viewSwitch = new HBox(8, journalViewBtn, auditViewBtn);
        viewSwitch.getStyleClass().add("toolbar");
        viewSwitch.setAlignment(Pos.CENTER_LEFT);

        StackPane viewContainer = new StackPane();

        // 1. Journal View components
        TextField transactionFilter = new TextField();
        transactionFilter.setPromptText("Filter by transaction ID");
        TextField accountFilter = new TextField();
        accountFilter.setPromptText("Filter by account");
        ComboBox<LedgerEntryType> type = new ComboBox<>(FXCollections.observableArrayList(LedgerEntryType.values()));
        type.setPromptText("All entries");
        Supplier<List<LedgerEntry>> filteredLedgerSupplier = () -> {
            String transactionQuery = transactionFilter.getText() == null ? "" : transactionFilter.getText().trim().toLowerCase();
            String accountQuery = accountFilter.getText() == null ? "" : accountFilter.getText().trim().toLowerCase();
            return ledgerRepository.findAll().stream()
                    .filter(entry -> transactionQuery.isEmpty() || entry.getTransactionId().toLowerCase().contains(transactionQuery))
                    .filter(entry -> accountQuery.isEmpty() || entry.getAccountId().toLowerCase().contains(accountQuery))
                    .filter(entry -> type.getValue() == null || entry.getEntryType() == type.getValue()).toList();
        };

        Button refreshJournal = secondaryButton("Refresh");
        Button exportLedgerCsv = secondaryButton("⬇ Export CSV");
        HBox journalToolbar = new HBox(12, transactionFilter, accountFilter, type, refreshJournal, exportLedgerCsv);
        journalToolbar.getStyleClass().add("toolbar");
        HBox.setHgrow(transactionFilter, Priority.ALWAYS);
        TableView<LedgerEntry> journalTable = ledgerTable();

        HBox totalsBox = new HBox(16);
        totalsBox.getStyleClass().add("ledger-total");
        totalsBox.setAlignment(Pos.CENTER_LEFT);

        Label debitLabel = new Label();
        debitLabel.getStyleClass().add("ledger-total-text");
        Label creditLabel = new Label();
        creditLabel.getStyleClass().add("ledger-total-text");
        Label balanceBadge = new Label();
        balanceBadge.getStyleClass().add("badge");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        totalsBox.getChildren().addAll(debitLabel, new Separator(), creditLabel, spacer, balanceBadge);

        VBox journalPanel = panel(journalToolbar, journalTable, totalsBox);
        VBox.setVgrow(journalPanel, Priority.ALWAYS);

        Runnable reloadJournal = () -> {
            List<LedgerEntry> entries = filteredLedgerSupplier.get();
            journalTable.setItems(FXCollections.observableArrayList(entries));
            journalTable.setPlaceholder(emptyState("No ledger entries available", "Financial transactions will generate double-entry records here.", null));
            BigDecimal debits = entries.stream().filter(entry -> entry.getEntryType() == LedgerEntryType.DEBIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credits = entries.stream().filter(entry -> entry.getEntryType() == LedgerEntryType.CREDIT).map(LedgerEntry::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);

            debitLabel.setText("Total Debits: " + FormatUtil.money(debits));
            creditLabel.setText("Total Credits: " + FormatUtil.money(credits));
            boolean balanced = debits.compareTo(credits) == 0;
            balanceBadge.setText(balanced ? "BALANCED" : "CHECK REQUIRED");
            balanceBadge.getStyleClass().removeAll("ledger-badge-balanced", "ledger-badge-unbalanced");
            balanceBadge.getStyleClass().add(balanced ? "ledger-badge-balanced" : "ledger-badge-unbalanced");
        };
        transactionFilter.textProperty().addListener((observable, oldValue, newValue) -> reloadJournal.run());
        accountFilter.textProperty().addListener((observable, oldValue, newValue) -> reloadJournal.run());
        type.valueProperty().addListener((observable, oldValue, newValue) -> reloadJournal.run());
        refreshJournal.setOnAction(event -> reloadJournal.run());
        exportLedgerCsv.setOnAction(event -> {
            List<LedgerEntry> matching = filteredLedgerSupplier.get();
            if (matching.isEmpty()) {
                AlertUtil.info("No Records to Export", "There are no ledger entries matching your current filter criteria.");
                return;
            }
            exportLedgerCsv(matching, exportLedgerCsv);
        });

        // 2. Audit & Reconciliation View components
        VBox auditPanel = new VBox(16);
        auditPanel.setFillWidth(true);

        viewGroup.selectedToggleProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == auditViewBtn) {
                renderAuditPanel(auditPanel);
                viewContainer.getChildren().setAll(auditPanel);
            } else {
                if (newVal == null) {
                    journalViewBtn.setSelected(true);
                    return;
                }
                reloadJournal.run();
                viewContainer.getChildren().setAll(journalPanel);
            }
        });

        reloadJournal.run();
        viewContainer.getChildren().setAll(journalPanel);
        VBox.setVgrow(viewContainer, Priority.ALWAYS);

        page.getChildren().addAll(viewSwitch, viewContainer);
        show(page);
    }

    private void renderAuditPanel(VBox auditPanel) {
        auditPanel.getChildren().clear();
        ReconciliationReport report = ledgerService.generateReconciliationReport(accountService.listAccounts(), this::bankName);

        HBox auditStats = new HBox(16,
                statCard("TOTAL DEBITS", FormatUtil.money(report.totalDebits()), "Sum of all ledger debits"),
                statCard("TOTAL CREDITS", FormatUtil.money(report.totalCredits()), "Sum of all ledger credits"),
                statCard("JOURNAL INVARIANT", report.isJournalBalanced() ? "BALANCED" : "UNBALANCED", "Debits equal Credits"),
                statCard("AUDIT STATUS", report.isFullyReconciled() ? "RECONCILED" : report.discrepancyCount() + " DISCREPANCIES", "Account vs Ledger"));
        auditStats.getStyleClass().add("stat-row");

        VBox alertBox = new VBox(8);
        if (!report.isJournalBalanced()) {
            Label unbalWarn = new Label("⚠ CRITICAL JOURNAL INVARIANT FAILURE: Overall Debits do not equal Credits, or "
                    + report.unbalancedTransactions().size() + " transaction(s) have unbalanced legs: "
                    + String.join(", ", report.unbalancedTransactions()));
            unbalWarn.getStyleClass().addAll("audit-callout-text", "badge-danger");
            unbalWarn.setWrapText(true);
            HBox warnRow = new HBox(unbalWarn);
            warnRow.getStyleClass().add("audit-callout-danger");
            alertBox.getChildren().add(warnRow);
        }
        if (report.hasDiscrepancies()) {
            Label discWarn = new Label("⚠ RECONCILIATION DISCREPANCY: " + report.discrepancyCount()
                    + " account(s) have balances that do not match their calculated ledger history (Opening Balance + Credits - Debits).");
            discWarn.getStyleClass().addAll("audit-callout-text", "badge-danger");
            discWarn.setWrapText(true);
            HBox warnRow = new HBox(discWarn);
            warnRow.getStyleClass().add("audit-callout-danger");
            alertBox.getChildren().add(warnRow);
        }
        if (report.isFullyReconciled()) {
            Label successNotice = new Label("✔ GENERAL LEDGER AUDIT PASSED: 100% of journal transactions are balanced and all account balances strictly reconcile with their immutable double-entry history.");
            successNotice.getStyleClass().addAll("audit-callout-text", "badge-success");
            successNotice.setWrapText(true);
            HBox successRow = new HBox(successNotice);
            successRow.getStyleClass().add("audit-callout-success");
            alertBox.getChildren().add(successRow);
        }

        TableView<AccountReconciliationRecord> reconTable = new TableView<>(FXCollections.observableArrayList(report.accountReconciliations()));
        reconTable.getStyleClass().add("data-table");
        reconTable.getColumns().addAll(
                column("Account ID", AccountReconciliationRecord::accountId, 130),
                column("Customer", AccountReconciliationRecord::customerName, 150),
                column("Bank", AccountReconciliationRecord::bankName, 130),
                moneyColumn("Opening Balance", AccountReconciliationRecord::initialBalance, 130),
                moneyColumn("Total Debits", AccountReconciliationRecord::totalDebits, 120),
                moneyColumn("Total Credits", AccountReconciliationRecord::totalCredits, 120),
                moneyColumn("Ledger Balance", AccountReconciliationRecord::ledgerDerivedBalance, 130),
                moneyColumn("Actual Balance", AccountReconciliationRecord::actualBalance, 130),
                moneyColumn("Discrepancy", AccountReconciliationRecord::discrepancy, 120),
                reconciledStatusColumn());
        reconTable.setPlaceholder(emptyState("No customer accounts found", "Create customer accounts to run the reconciliation audit.", null));

        Button refreshAuditBtn = secondaryButton("Re-Run Audit");
        refreshAuditBtn.setOnAction(e -> renderAuditPanel(auditPanel));
        HBox auditActionBox = new HBox(refreshAuditBtn);
        auditActionBox.setAlignment(Pos.CENTER_RIGHT);

        VBox reconCard = panel(sectionLabel("Account Balance Reconciliation"), alertBox, reconTable, auditActionBox);
        VBox.setVgrow(reconCard, Priority.ALWAYS);
        auditPanel.getChildren().addAll(auditStats, reconCard);
    }

    private TableColumn<AccountReconciliationRecord, String> reconciledStatusColumn() {
        TableColumn<AccountReconciliationRecord, String> column = new TableColumn<>("Audit Status");
        column.setPrefWidth(130);
        column.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null || getTableRow().getItem() == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    AccountReconciliationRecord record = getTableRow().getItem();
                    Label badge = new Label(record.isReconciled() ? "RECONCILED" : "DISCREPANCY");
                    badge.getStyleClass().addAll("badge", record.isReconciled() ? "badge-success" : "badge-danger");
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        return column;
    }

    private TableView<LedgerEntry> ledgerTable() {
        TableView<LedgerEntry> table = new TableView<>();
        table.getStyleClass().add("data-table");
        TableColumn<LedgerEntry, BigDecimal> debit = entryAmountColumn("Debit",
                entry -> entry.getEntryType() == LedgerEntryType.DEBIT ? entry.getAmount() : null, "debit-amount", 130);
        TableColumn<LedgerEntry, BigDecimal> credit = entryAmountColumn("Credit",
                entry -> entry.getEntryType() == LedgerEntryType.CREDIT ? entry.getAmount() : null, "credit-amount", 130);
        table.getColumns().addAll(
                column("Ledger ID", entry -> String.valueOf(entry.getLedgerEntryId()), 100),
                column("Transaction ID", LedgerEntry::getTransactionId, 320),
                column("Account", LedgerEntry::getAccountId, 140),
                column("Entry", entry -> entry.getEntryType().name(), 100),
                debit,
                credit,
                column("Description", entry -> valueOrDash(entry.getDescription()), 190),
                column("Timestamp", entry -> FormatUtil.dateTime(entry.getCreatedAt()), 180));
        return table;
    }

    private void exportTransactionsCsv(List<Transaction> matching, Button sourceButton) {
        sourceButton.setDisable(true);
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Transactions to CSV");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            String filename = "vaultledger_transactions_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
            fileChooser.setInitialFileName(filename);

            Window window = sourceButton.getScene() != null ? sourceButton.getScene().getWindow() : null;
            File targetFile = fileChooser.showSaveDialog(window);
            if (targetFile != null) {
                CsvExporter.writeTransactions(targetFile.toPath(), matching);
                AlertUtil.success("Export Successful", "Successfully exported " + matching.size() + " transaction record(s) to:\n" + targetFile.getAbsolutePath());
            }
        } catch (Exception ex) {
            AlertUtil.error(ex);
        } finally {
            sourceButton.setDisable(false);
        }
    }

    private void exportLedgerCsv(List<LedgerEntry> matching, Button sourceButton) {
        sourceButton.setDisable(true);
        try {
            FileChooser fileChooser = new FileChooser();
            fileChooser.setTitle("Export Double-Entry Ledger to CSV");
            fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files (*.csv)", "*.csv"));
            String filename = "vaultledger_ledger_" + java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".csv";
            fileChooser.setInitialFileName(filename);

            Window window = sourceButton.getScene() != null ? sourceButton.getScene().getWindow() : null;
            File targetFile = fileChooser.showSaveDialog(window);
            if (targetFile != null) {
                CsvExporter.writeLedger(targetFile.toPath(), matching);
                AlertUtil.success("Export Successful", "Successfully exported " + matching.size() + " ledger record(s) to:\n" + targetFile.getAbsolutePath());
            }
        } catch (Exception ex) {
            AlertUtil.error(ex);
        } finally {
            sourceButton.setDisable(false);
        }
    }

    private void showTransactionDialog(TransactionType transactionType, String presetAccount,
                                      String presetDestination, Runnable afterReceiptClosed) {
        String title = transactionType == TransactionType.TRANSFER ? "Transfer Funds"
                : transactionType == TransactionType.DEPOSIT ? "Deposit Funds" : "Withdraw Funds";
        DialogWindow dialog = new DialogWindow(title);

        VBox card = new VBox(16);
        card.getStyleClass().add("dialog-panel");

        Label heading = sectionLabel(title);

        GridPane form = new GridPane();
        form.setHgap(16);
        form.setVgap(14);
        ColumnConstraints labelCol = new ColumnConstraints(130);
        ColumnConstraints inputCol = new ColumnConstraints(310, 340, Double.MAX_VALUE);
        inputCol.setHgrow(Priority.ALWAYS);
        form.getColumnConstraints().addAll(labelCol, inputCol);

        List<Account> activeAccounts = accountService.listAccounts().stream()
                .filter(account -> account.getStatus() == AccountStatus.ACTIVE)
                .toList();

        ComboBox<Account> from = accountPicker(activeAccounts);
        ComboBox<Account> to = accountPicker(activeAccounts);
        TextField amount = input("0.00");
        TextField description = input("Description / Reference (optional)");

        if (presetAccount != null) {
            if (transactionType == TransactionType.DEPOSIT) {
                selectAccount(to, presetAccount);
            } else {
                selectAccount(from, presetAccount);
            }
        }

        if (transactionType == TransactionType.TRANSFER) {
            Runnable updateDestinationList = () -> {
                Account currentFrom = from.getValue();
                Account currentTo = to.getValue();
                List<Account> availableDests = activeAccounts.stream()
                        .filter(a -> currentFrom == null || !a.getAccountId().equalsIgnoreCase(currentFrom.getAccountId()))
                        .toList();
                to.setItems(FXCollections.observableArrayList(availableDests));
                if (currentTo != null && currentFrom != null && currentTo.getAccountId().equalsIgnoreCase(currentFrom.getAccountId())) {
                    to.setValue(null);
                } else if (currentTo != null && availableDests.contains(currentTo)) {
                    to.setValue(currentTo);
                }
                if (availableDests.isEmpty()) {
                    to.setPromptText("No other active accounts");
                } else {
                    to.setPromptText("Select destination account");
                }
            };
            from.valueProperty().addListener((obs, oldVal, newVal) -> updateDestinationList.run());
            updateDestinationList.run();

            if (presetDestination != null) {
                selectAccount(to, presetDestination);
            }
        }

        int row = 0;
        if (transactionType == TransactionType.DEPOSIT) {
            form.addRow(row++, formLabel("Account"), to);
        } else if (transactionType == TransactionType.WITHDRAWAL) {
            form.addRow(row++, formLabel("From Account"), from);
        } else {
            form.addRow(row++, formLabel("From Account"), from);
            form.addRow(row++, formLabel("To Account"), to);
        }
        form.addRow(row++, formLabel("Amount"), amount);
        form.addRow(row++, formLabel("Description"), description);

        // Preview label for live calculation & feedback
        Label preview = new Label();
        preview.getStyleClass().add("calculation-preview");
        preview.setMaxWidth(Double.MAX_VALUE);
        preview.managedProperty().bind(preview.visibleProperty());
        preview.setVisible(false);

        Runnable updatePreview = () -> {
            try {
                String amtStr = amount.getText() == null ? "" : amount.getText().trim();
                if (amtStr.isEmpty()) {
                    preview.setText("");
                    preview.setVisible(false);
                    return;
                }
                BigDecimal entered;
                try {
                    entered = new BigDecimal(amtStr);
                } catch (Exception ex) {
                    preview.setText("Invalid amount format");
                    preview.setVisible(true);
                    return;
                }
                if (entered.signum() <= 0) {
                    preview.setText("Amount must be greater than zero");
                    preview.setVisible(true);
                    return;
                }
                if (transactionType == TransactionType.DEPOSIT) {
                    Account selected = to.getValue();
                    if (selected != null) {
                        BigDecimal newBal = selected.getBalance().add(entered);
                        preview.setText("Current: " + FormatUtil.money(selected.getBalance())
                                + "   →   New Balance: " + FormatUtil.money(newBal));
                        preview.setVisible(true);
                    } else {
                        preview.setText("");
                        preview.setVisible(false);
                    }
                } else if (transactionType == TransactionType.WITHDRAWAL) {
                    Account selected = from.getValue();
                    if (selected != null) {
                        BigDecimal newBal = selected.getBalance().subtract(entered);
                        if (entered.compareTo(selected.getBalance()) > 0) {
                            preview.setText("Insufficient funds! Balance: " + FormatUtil.money(selected.getBalance())
                                    + "  |  Requested: " + FormatUtil.money(entered));
                        } else {
                            preview.setText("Current: " + FormatUtil.money(selected.getBalance())
                                    + "   →   New Balance: " + FormatUtil.money(newBal));
                        }
                        preview.setVisible(true);
                    } else {
                        preview.setText("");
                        preview.setVisible(false);
                    }
                } else {
                    Account src = from.getValue();
                    Account dst = to.getValue();
                    if (src != null && dst != null) {
                        if (entered.compareTo(src.getBalance()) > 0) {
                            preview.setText("Insufficient funds! " + src.getCustomerName() + " has " + FormatUtil.money(src.getBalance()));
                        } else {
                            preview.setText(src.getCustomerName() + ": " + FormatUtil.money(src.getBalance().subtract(entered))
                                    + "   |   " + dst.getCustomerName() + ": " + FormatUtil.money(dst.getBalance().add(entered)));
                        }
                        preview.setVisible(true);
                    } else if (src != null) {
                        if (entered.compareTo(src.getBalance()) > 0) {
                            preview.setText("Insufficient funds! Sender balance is " + FormatUtil.money(src.getBalance()));
                        } else {
                            preview.setText("Sender balance after: " + FormatUtil.money(src.getBalance().subtract(entered)));
                        }
                        preview.setVisible(true);
                    } else {
                        preview.setText("");
                        preview.setVisible(false);
                    }
                }
            } catch (Exception ignored) {
                preview.setText("");
                preview.setVisible(false);
            }
        };

        amount.textProperty().addListener((observable, oldValue, newValue) -> updatePreview.run());
        from.valueProperty().addListener((observable, oldValue, newValue) -> updatePreview.run());
        to.valueProperty().addListener((observable, oldValue, newValue) -> updatePreview.run());

        Button submit = primaryButton(transactionType == TransactionType.DEPOSIT ? "Deposit"
                : transactionType == TransactionType.WITHDRAWAL ? "Withdraw" : "Transfer");
        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(event -> dialog.close());

        submit.setOnAction(event -> {
            submit.setDisable(true);
            try {
                String amtStr = amount.getText() == null ? "" : amount.getText().trim();
                if (amtStr.isEmpty()) {
                    throw new IllegalArgumentException("Please enter a transaction amount.");
                }
                BigDecimal value;
                try {
                    value = new BigDecimal(amtStr);
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Amount must be a valid number (e.g. 100.00).");
                }
                if (value.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new IllegalArgumentException("Transaction amount must be greater than zero.");
                }

                Transaction result;
                if (transactionType == TransactionType.DEPOSIT) {
                    Account dest = to.getValue();
                    if (dest == null) {
                        throw new IllegalArgumentException("Please select an account to deposit funds into.");
                    }
                    result = transactionService.deposit(dest.getAccountId(), value, description.getText());
                } else if (transactionType == TransactionType.WITHDRAWAL) {
                    Account src = from.getValue();
                    if (src == null) {
                        throw new IllegalArgumentException("Please select an account to withdraw funds from.");
                    }
                    result = transactionService.withdraw(src.getAccountId(), value, description.getText());
                } else {
                    Account src = from.getValue();
                    Account dst = to.getValue();
                    if (src == null) {
                        throw new IllegalArgumentException("Please select a source account.");
                    }
                    if (dst == null) {
                        throw new IllegalArgumentException("Please select a destination account.");
                    }
                    if (src.getAccountId().equalsIgnoreCase(dst.getAccountId())) {
                        throw new IllegalArgumentException("Source and destination accounts must be different.");
                    }
                    result = transactionService.transfer(src.getAccountId(), dst.getAccountId(), value, description.getText());
                }
                dialog.close();
                showReceipt(result, afterReceiptClosed);
            } catch (Exception exception) {
                AlertUtil.error(exception);
                submit.setDisable(false);
            }
        });

        HBox actions = new HBox(12, submit, cancel);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setPadding(new Insets(4, 0, 0, 0));

        card.getChildren().addAll(heading, form, preview, actions);
        dialog.setContent(card);
        dialog.show();
    }

    private ComboBox<Account> accountPicker(List<Account> accounts) {
        ComboBox<Account> picker = new ComboBox<>(FXCollections.observableArrayList(accounts));
        picker.setPromptText("Select account");
        picker.setMaxWidth(Double.MAX_VALUE);
        picker.setCellFactory(list -> accountCell());
        picker.setButtonCell(accountCell());
        return picker;
    }

    private void selectAccount(ComboBox<Account> picker, String accountId) {
        if (picker == null || accountId == null) {
            return;
        }
        for (Account account : picker.getItems()) {
            if (account.getAccountId().equalsIgnoreCase(accountId)) {
                picker.getSelectionModel().select(account);
                picker.setValue(account);
                return;
            }
        }
    }

    private ListCell<Account> accountCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(Account item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getAccountId() + "  |  " + item.getCustomerName() + " (" + FormatUtil.money(item.getBalance()) + ")");
                }
            }
        };
    }

    private void showReceipt(Transaction transaction, Runnable afterClosed) {
        DialogWindow dialog = new DialogWindow("Transaction Receipt");
        VBox receipt = new VBox(12);
        receipt.getStyleClass().add("receipt");
        Label title = new Label("TRANSACTION RECEIPT");
        title.getStyleClass().add("receipt-title");
        receipt.getChildren().add(title);
        receipt.getChildren().addAll(
                receiptRow("Transaction ID", transaction.getTransactionId()),
                receiptRow("Date / Time", FormatUtil.dateTime(transaction.getCreatedAt())),
                receiptRow("Type", transaction.getTransactionType().name()));

        if (transaction.getTransactionType() == TransactionType.DEPOSIT) {
            String toAccId = transaction.getToAccount();
            Account toAcc = null;
            try { toAcc = accountService.findAccount(toAccId); } catch (Exception ignored) {}
            String bankInfo = toAcc != null ? bankName(toAcc.getBankId()) : "-";
            String customerInfo = toAcc != null ? toAcc.getCustomerName() : "-";
            receipt.getChildren().addAll(
                    receiptRow("Deposited To", toAccId + " (" + customerInfo + ")"),
                    receiptRow("Bank", bankInfo),
                    receiptRow("Deposit Amount", FormatUtil.money(transaction.getAmount())));
            if (toAcc != null) {
                receipt.getChildren().add(receiptRow("Confirmed Balance", FormatUtil.money(toAcc.getBalance())));
            }
        } else if (transaction.getTransactionType() == TransactionType.WITHDRAWAL) {
            String fromAccId = transaction.getFromAccount();
            Account fromAcc = null;
            try { fromAcc = accountService.findAccount(fromAccId); } catch (Exception ignored) {}
            String bankInfo = fromAcc != null ? bankName(fromAcc.getBankId()) : "-";
            String customerInfo = fromAcc != null ? fromAcc.getCustomerName() : "-";
            receipt.getChildren().addAll(
                    receiptRow("Withdrawn From", fromAccId + " (" + customerInfo + ")"),
                    receiptRow("Bank", bankInfo),
                    receiptRow("Withdrawal Amount", FormatUtil.money(transaction.getAmount())));
            if (fromAcc != null) {
                receipt.getChildren().add(receiptRow("Confirmed Balance", FormatUtil.money(fromAcc.getBalance())));
            }
        } else { // TRANSFER
            String fromAccId = transaction.getFromAccount();
            String toAccId = transaction.getToAccount();
            Account fromAcc = null;
            Account toAcc = null;
            try { fromAcc = accountService.findAccount(fromAccId); } catch (Exception ignored) {}
            try { toAcc = accountService.findAccount(toAccId); } catch (Exception ignored) {}

            boolean isInterBank = fromAcc != null && toAcc != null && !fromAcc.getBankId().equals(toAcc.getBankId());
            String scope = isInterBank
                    ? "Inter-Bank Transfer (" + bankName(fromAcc.getBankId()) + " → " + bankName(toAcc.getBankId()) + ")"
                    : "Intra-Bank Transfer (" + (fromAcc != null ? bankName(fromAcc.getBankId()) : "Same Bank") + ")";

            receipt.getChildren().addAll(
                    receiptRow("Transfer Scope", scope),
                    receiptRow("From Account", fromAccId + " (" + (fromAcc != null ? fromAcc.getCustomerName() : "-") + ")"),
                    receiptRow("To Account", toAccId + " (" + (toAcc != null ? toAcc.getCustomerName() : "-") + ")"),
                    receiptRow("Transfer Amount", FormatUtil.money(transaction.getAmount())));

            if (fromAcc != null) {
                receipt.getChildren().add(receiptRow("Sender Balance", FormatUtil.money(fromAcc.getBalance())));
            }
            if (toAcc != null) {
                receipt.getChildren().add(receiptRow("Recipient Balance", FormatUtil.money(toAcc.getBalance())));
            }
        }

        receipt.getChildren().addAll(
                receiptRow("Status", transaction.getStatus().name()),
                receiptRow("Description", valueOrDash(transaction.getDescription())));
        Label success = new Label("SUCCESS — COMMITTED");
        success.getStyleClass().add("receipt-success");
        Button close = primaryButton("Close Receipt");
        close.setOnAction(event -> dialog.close());
        VBox card = new VBox(14);
        card.getStyleClass().add("dialog-panel");
        card.getChildren().addAll(receipt, success, close);
        dialog.setContent(card);
        dialog.show();
        if (afterClosed != null) {
            afterClosed.run();
        }
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

    private void showResetDemoData() {
        DialogWindow dialog = new DialogWindow("Reset Demo Data");
        VBox content = new VBox(14);
        content.getStyleClass().add("dialog-panel");
        
        Label title = new Label("Reset Demo Data");
        title.getStyleClass().add("dialog-header-title");
        
        Label body = new Label("Are you sure you want to remove all demo records?");
        body.getStyleClass().add("dialog-body-text");
        
        Label stats = new Label("Affected records:\n• " + dbmService.getDemoAccountCount() + " Demo Accounts\n• " + dbmService.getDemoTransactionCount() + " Demo Transactions");
        stats.getStyleClass().add("dialog-body-text");
        
        Label helper = new Label("User-created accounts and transactions will be safely preserved.");
        helper.getStyleClass().add("dialog-helper-text");
        
        Button confirm = dangerButton("Reset Demo Data");
        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(e -> dialog.close());
        confirm.setOnAction(e -> {
            try {
                dbmService.resetDemoData();
                dialog.close();
                showDashboard();
                AlertUtil.success("Demo Data Reset", "Demo data has been successfully removed.");
            } catch (Exception ex) {
                AlertUtil.error(ex);
            }
        });
        content.getChildren().addAll(title, body, stats, helper, new HBox(12, confirm, cancel));
        dialog.setContent(content);
        dialog.show();
    }

    private void showResetEntireDatabase() {
        DialogWindow dialog = new DialogWindow("Reset Entire Local Workspace");
        VBox content = new VBox(14);
        content.getStyleClass().add("dialog-panel");
        
        Label title = new Label("Reset Entire Local Workspace");
        title.getStyleClass().add("dialog-header-title");
        
        Label warning = new Label("WARNING: This will permanently remove ALL banking records!");
        warning.getStyleClass().add("dialog-warning-text");
        
        Label note = new Label("Database schema, version, and configuration will remain intact.");
        note.getStyleClass().add("dialog-helper-text");
        
        Label confirmInstruction = new Label("Type 'RESET ALL DATA' to confirm:");
        confirmInstruction.getStyleClass().add("form-label");
        
        TextField confirmText = new TextField();
        confirmText.setPromptText("Type RESET ALL DATA");
        Button confirm = dangerButton("Reset Entire Database");
        confirm.setDisable(true);
        confirmText.textProperty().addListener((obs, old, newVal) -> {
            confirm.setDisable(!"RESET ALL DATA".equals(newVal));
        });
        
        Button cancel = secondaryButton("Cancel");
        cancel.setOnAction(e -> dialog.close());
        confirm.setOnAction(e -> {
            try {
                dbmService.resetEntireDatabase();
                dialog.close();
                showDashboard();
                AlertUtil.success("Database Reset", "All local banking records have been permanently removed.");
            } catch (Exception ex) {
                AlertUtil.error(ex);
            }
        });
        
        content.getChildren().addAll(title, warning, note, confirmInstruction, confirmText, new HBox(12, confirm, cancel));
        dialog.setContent(content);
        dialog.show();
    }

    private VBox page() {
        VBox page = new VBox(18);
        page.getStyleClass().add("page");
        page.setFillWidth(true);
        return page;
    }

    private VBox panel(Node... nodes) {
        VBox panel = new VBox(14, nodes);
        panel.getStyleClass().add("panel");
        panel.setFillWidth(true);
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
        Label label = new Label(text);
        label.getStyleClass().add("detail-label");
        label.setMinWidth(140);
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
        ColumnConstraints labelCol = new ColumnConstraints(150);
        ColumnConstraints inputCol = new ColumnConstraints(380, 420, Double.MAX_VALUE);
        inputCol.setHgrow(Priority.ALWAYS);
        grid.getColumnConstraints().addAll(labelCol, inputCol);
        return grid;
    }

    private Button primaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", "primary-button");
        return button;
    }

    private Button secondaryButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", "secondary-button");
        return button;
    }

    private Button dangerButton(String text) {
        Button button = new Button(text);
        button.getStyleClass().addAll("button", "danger-button");
        return button;
    }

    private <T> TableColumn<T, String> column(String title, Function<T, String> value, double width) {
        TableColumn<T, String> column = new TableColumn<>(title);
        column.setCellValueFactory(data -> new SimpleStringProperty(value.apply(data.getValue())));
        column.setPrefWidth(width);
        return column;
    }

    private <T> TableColumn<T, String> statusColumn(String title, Function<T, String> valueExtractor, double width) {
        TableColumn<T, String> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleStringProperty(valueExtractor.apply(data.getValue())));
        col.setPrefWidth(width);
        col.setCellFactory(column -> new TableCell<>() {
            private final Label badge = new Label();
            {
                badge.getStyleClass().add("badge");
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    setText(null);
                } else {
                    badge.setText(item);
                    badge.getStyleClass().removeAll("badge-success", "badge-warning", "badge-danger", "badge-neutral");
                    if ("ACTIVE".equalsIgnoreCase(item) || "SUCCESS".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-success");
                    } else if ("INACTIVE".equalsIgnoreCase(item) || "PENDING".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-warning");
                    } else if ("CLOSED".equalsIgnoreCase(item) || "FAILED".equalsIgnoreCase(item)) {
                        badge.getStyleClass().add("badge-danger");
                    } else {
                        badge.getStyleClass().add("badge-neutral");
                    }
                    setGraphic(badge);
                    setText(null);
                }
            }
        });
        return col;
    }

    private <T> TableColumn<T, String> moneyColumn(String title, Function<T, BigDecimal> value, double width) {
        TableColumn<T, String> col = column(title, row -> FormatUtil.money(value.apply(row)), width);
        col.getStyleClass().add("money-column");
        return col;
    }

    private TableColumn<LedgerEntry, BigDecimal> entryAmountColumn(String title, Function<LedgerEntry, BigDecimal> value, String styleClass, double width) {
        TableColumn<LedgerEntry, BigDecimal> col = new TableColumn<>(title);
        col.setCellValueFactory(data -> new SimpleObjectProperty<>(value.apply(data.getValue())));
        col.setPrefWidth(width);
        col.getStyleClass().add("money-column");
        col.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(BigDecimal amount, boolean empty) {
                super.updateItem(amount, empty);
                if (empty || amount == null) {
                    setText("-");
                    getStyleClass().remove(styleClass);
                } else {
                    setText(FormatUtil.money(amount));
                    if (!getStyleClass().contains(styleClass)) {
                        getStyleClass().add(styleClass);
                    }
                }
            }
        });
        return col;
    }

    private Node emptyState(String title, String subtitle, Node actionNode) {
        VBox box = new VBox(10);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("empty-state-box");
        
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("empty-state-title");
        
        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("empty-state-subtitle");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
        
        box.getChildren().addAll(titleLabel, subtitleLabel);
        if (actionNode != null) {
            box.getChildren().add(actionNode);
        }
        return box;
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
        private final StackPane root = new StackPane();

        private DialogWindow(String title) {
            stage.initModality(Modality.APPLICATION_MODAL);
            stage.setTitle(title);
            stage.setResizable(true);
            stage.setMinWidth(520);
        }

        private void setContent(Node node) {
            root.getChildren().setAll(node);
            root.getStyleClass().add("dialog-content");
            Scene scene = new Scene(root);
            var css = AppShell.class.getResource("/css/application.css");
            if (css != null) {
                scene.getStylesheets().add(css.toExternalForm());
            }
            stage.setScene(scene);
            stage.sizeToScene();
        }

        private void show() {
            stage.showAndWait();
        }

        private void close() {
            stage.close();
        }
    }
}
