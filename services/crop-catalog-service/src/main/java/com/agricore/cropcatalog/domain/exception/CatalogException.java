package com.agricore.cropcatalog.domain.exception;

public class CatalogException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public CatalogException(String code, String message, int httpStatus) {
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
