package com.agricore.inventory.domain.exception;

public class InventoryException extends RuntimeException {
    private final String code;
    private final int httpStatus;

    public InventoryException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() { return code; }
    public int getHttpStatus() { return httpStatus; }
}
