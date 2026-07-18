package com.agricore.identity.domain.exception;

public final class InvalidCredentialsException extends IdentityException {

    public InvalidCredentialsException() {
        super("INVALID_CREDENTIALS", "Invalid email or password", 401);
    }
}
