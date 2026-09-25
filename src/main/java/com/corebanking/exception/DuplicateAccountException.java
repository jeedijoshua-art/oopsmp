package com.corebanking.exception;

public class DuplicateAccountException extends RuntimeException {
    public DuplicateAccountException(String accountId) {
        super("Account ID already exists: " + accountId);
    }
}
