package com.corebanking.auth;

import java.util.Objects;

/**
 * Result of an administrative authentication attempt.
 */
public record AuthResult(boolean success, String message, AuthFailureReason failureReason, String username) {

    public static AuthResult success(String username, String message) {
        return new AuthResult(true, Objects.requireNonNull(message), null, Objects.requireNonNull(username));
    }

    public static AuthResult failure(AuthFailureReason reason, String message) {
        return new AuthResult(false, Objects.requireNonNull(message), Objects.requireNonNull(reason), null);
    }
}

