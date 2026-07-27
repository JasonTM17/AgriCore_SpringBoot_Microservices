package com.agricore.identity.domain.exception;

public final class RefreshTokenReuseException extends IdentityException {

    public RefreshTokenReuseException() {
        super(
                "REFRESH_TOKEN_REUSE",
                "Refresh token reuse detected. Session family revoked.",
                401
        );
    }
}
