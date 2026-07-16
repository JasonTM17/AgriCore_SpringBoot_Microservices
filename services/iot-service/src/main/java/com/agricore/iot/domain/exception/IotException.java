package com.agricore.iot.domain.exception;

public class IotException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public IotException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
