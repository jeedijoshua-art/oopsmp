package com.corebanking.ui.util;

import com.corebanking.exception.AccountClosureException;
import com.corebanking.exception.AccountNotFoundException;
import com.corebanking.exception.DataAccessException;
import com.corebanking.exception.DuplicateAccountException;
import com.corebanking.exception.InsufficientFundsException;
import com.corebanking.exception.InvalidAccountDataException;
import com.corebanking.exception.InvalidAccountStateException;
import com.corebanking.exception.InvalidAmountException;
import com.corebanking.exception.InvalidTransferException;
import com.corebanking.exception.TransactionFailedException;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

public final class AlertUtil {
    private AlertUtil() {
    }

    public static void error(Throwable error) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Action could not be completed");
        alert.setHeaderText(header(error));
        alert.setContentText(message(error));
        styleAlert(alert);
        alert.showAndWait();
    }

    public static void success(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    public static void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        styleAlert(alert);
        alert.showAndWait();
    }

    public static boolean confirm(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.CANCEL, ButtonType.OK);
        alert.setTitle(title);
        alert.setHeaderText(null);
        styleAlert(alert);
        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private static void styleAlert(Alert alert) {
        try {
            var cssUrl = AlertUtil.class.getResource("/css/application.css");
            if (cssUrl != null) {
                alert.getDialogPane().getStylesheets().add(cssUrl.toExternalForm());
                alert.getDialogPane().getStyleClass().add("banking-alert");
            }
        } catch (Exception ignored) {
            // Fallback gracefully if graphics environment or resource cannot load
        }
    }

    private static String header(Throwable error) {
        if (error instanceof InsufficientFundsException) {
            return "Transaction declined: insufficient funds.";
        }
        if (error instanceof InvalidAccountStateException) {
            return "Account is not available for this operation.";
        }
        if (error instanceof DuplicateAccountException) {
            return "Duplicate account ID.";
        }
        if (error instanceof AccountNotFoundException) {
            return "Account not found.";
        }
        if (error instanceof InvalidAmountException) {
            return "Invalid amount.";
        }
        if (error instanceof InvalidTransferException) {
            return "Invalid transfer.";
        }
        if (error instanceof AccountClosureException) {
            return "Account cannot be closed.";
        }
        if (error instanceof InvalidAccountDataException) {
            return "Check the account details.";
        }
        if (error instanceof DataAccessException || error instanceof TransactionFailedException) {
            return "No changes were applied.";
        }
        return "Something went wrong.";
    }

    private static String message(Throwable error) {
        if (error instanceof InvalidAccountStateException && error.getMessage() != null
                && error.getMessage().toLowerCase().contains("closed")) {
            return "This account is closed.";
        }
        if (error instanceof InvalidAccountStateException) {
            return "This account is inactive and cannot perform transactions.";
        }
        return error.getMessage() == null ? "Please review the request and try again." : error.getMessage();
    }
}
