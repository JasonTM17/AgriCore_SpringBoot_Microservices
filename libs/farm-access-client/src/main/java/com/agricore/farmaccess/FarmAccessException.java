package com.agricore.farmaccess;

public class FarmAccessException extends RuntimeException {

    private final String code;
    private final int httpStatus;

    public FarmAccessException(String code, String message, int httpStatus) {
        super(message);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    private FarmAccessException(String code, String message, int httpStatus, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public static FarmAccessException scopeRequired() {
        return new FarmAccessException(
                "FARM_SCOPE_REQUIRED",
                "A farm or plot scope is required",
                400
        );
    }

    static FarmAccessException denied() {
        return new FarmAccessException("FARM_ACCESS_DENIED", "Farm access denied", 403);
    }

    static FarmAccessException notFound() {
        return new FarmAccessException("FARM_RESOURCE_NOT_FOUND", "Farm resource not found", 404);
    }

    static FarmAccessException unavailable() {
        return new FarmAccessException(
                "FARM_ACCESS_UNAVAILABLE",
                "Farm access verification is temporarily unavailable",
                503
        );
    }

    static FarmAccessException unavailable(Throwable cause) {
        return new FarmAccessException(
                "FARM_ACCESS_UNAVAILABLE",
                "Farm access verification is temporarily unavailable",
                503,
                cause
        );
    }
}
