package com.agricore.sales.domain.model;

public enum OrderStatus {
    DRAFT,
    PENDING_CONFIRMATION,
    CONFIRMED,
    STOCK_RESERVED,
    PROCESSING,
    SHIPPED,
    DELIVERED,
    CANCELLED,
    OUT_OF_STOCK
}
