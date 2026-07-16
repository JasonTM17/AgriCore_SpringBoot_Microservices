package com.agricore.cropcycle.domain.exception;

public class CropCycleException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public CropCycleException(String code, String message, int httpStatus) {
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
