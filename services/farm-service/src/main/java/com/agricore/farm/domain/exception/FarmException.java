package com.agricore.farm.domain.exception;

public class FarmException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public FarmException(String code, String message, int httpStatus) {
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
