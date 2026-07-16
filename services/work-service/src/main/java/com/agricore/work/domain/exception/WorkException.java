package com.agricore.work.domain.exception;

public class WorkException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public WorkException(String code, String message, int httpStatus) {
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
