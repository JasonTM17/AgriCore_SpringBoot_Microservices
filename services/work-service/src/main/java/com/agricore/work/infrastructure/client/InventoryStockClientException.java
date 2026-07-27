package com.agricore.work.infrastructure.client;

public class InventoryStockClientException extends RuntimeException {

    private final int downstreamStatus;

    private InventoryStockClientException(String message, int downstreamStatus, Throwable cause) {
        super(message, cause);
        this.downstreamStatus = downstreamStatus;
    }

    public static InventoryStockClientException downstream(int status) {
        return new InventoryStockClientException(
                "Inventory stock-out returned HTTP " + status,
                status,
                null
        );
    }

    public static InventoryStockClientException unavailable(Throwable cause) {
        return new InventoryStockClientException("Inventory stock-out is unavailable", 503, cause);
    }

    public int getDownstreamStatus() {
        return downstreamStatus;
    }
}
