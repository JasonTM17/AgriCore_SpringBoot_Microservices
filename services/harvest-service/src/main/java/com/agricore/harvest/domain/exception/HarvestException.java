package com.agricore.harvest.domain.exception;

public class HarvestException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public HarvestException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
