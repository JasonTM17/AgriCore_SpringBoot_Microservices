package com.agricore.identity.domain.exception;

public class IdentityException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public IdentityException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
