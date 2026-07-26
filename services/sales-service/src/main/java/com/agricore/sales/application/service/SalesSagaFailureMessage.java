package com.agricore.sales.application.service;

import com.agricore.sales.infrastructure.client.InventoryClient;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Keeps saga diagnostics bounded and prevents arbitrary exception text from
 * crossing persistence or API boundaries.
 */
final class SalesSagaFailureMessage {

    static final int MAX_LENGTH = 160;

    private static final String DEFAULT_MESSAGE = "Inventory saga failed";
    private static final Pattern SAFE_INVENTORY_FAILURE = Pattern.compile(
            "Inventory request failed \\(status=(\\d{3}), code=([A-Z][A-Z0-9_]{0,63})\\)"
    );
    private static final Set<String> SAFE_ERROR_CODES = Set.of(
            "INSUFFICIENT_STOCK",
            "RESERVATION_REFERENCE_CONFLICT",
            "INVENTORY_DOWNSTREAM_ERROR",
            "INVALID_INVENTORY_RESPONSE",
            "INVENTORY_UNAVAILABLE",
            "INVENTORY_CLIENT_CONFIGURATION_ERROR"
    );

    private SalesSagaFailureMessage() {
    }

    static String from(Exception failure, String fallback) {
        if (failure instanceof InventoryClient.InventoryReservationException inventoryFailure) {
            return bounded(inventoryFailure.getMessage(), fallback);
        }
        return bounded(fallback, DEFAULT_MESSAGE);
    }

    static String bounded(String value) {
        return bounded(value, DEFAULT_MESSAGE);
    }

    static String bounded(String value, String fallback) {
        String source = value == null || value.isBlank() ? fallback : value;
        StringBuilder normalized = new StringBuilder(Math.min(source.length(), MAX_LENGTH));
        boolean previousWasSpace = false;
        for (int index = 0; index < source.length() && normalized.length() < MAX_LENGTH; index++) {
            char current = source.charAt(index);
            boolean whitespace = Character.isWhitespace(current) || Character.isISOControl(current);
            if (whitespace) {
                if (!previousWasSpace && !normalized.isEmpty()) {
                    normalized.append(' ');
                }
            } else {
                normalized.append(current);
            }
            previousWasSpace = whitespace;
        }
        String result = normalized.toString().trim();
        return result.isEmpty() ? DEFAULT_MESSAGE : result;
    }

    static String publicMessage(String storedMessage) {
        if (storedMessage == null) {
            return null;
        }
        String boundedStoredMessage = bounded(storedMessage);
        Matcher safeFailure = SAFE_INVENTORY_FAILURE.matcher(boundedStoredMessage);
        if (safeFailure.find() && SAFE_ERROR_CODES.contains(safeFailure.group(2))) {
            return "Inventory request failed (status="
                    + safeFailure.group(1)
                    + ", code="
                    + safeFailure.group(2)
                    + ")";
        }
        if ("reconciled:RELEASE".equals(boundedStoredMessage)) {
            return boundedStoredMessage;
        }
        return DEFAULT_MESSAGE;
    }
}
