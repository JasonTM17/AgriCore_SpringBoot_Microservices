package com.agricore.inventory.api.advice;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    private final MockHttpServletRequest request = new MockHttpServletRequest(
            "POST",
            "/api/v1/inventory/reservations"
    );

    @Test
    void reservationReferenceConstraintMapsToConflictWithoutLeakingSql() {
        DataIntegrityViolationException failure = violation(
                "uk_inventory_reservations_reference"
        );

        var response = handler.dataIntegrity(failure, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESERVATION_REFERENCE_CONFLICT");
        assertThat(response.getBody().message()).doesNotContain("duplicate key");
    }

    @Test
    void unrelatedConstraintRemainsGenericServerError() {
        var response = handler.dataIntegrity(violation("unknown_constraint"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY_ERROR");
        assertThat(response.getBody().message()).doesNotContain("unknown_constraint");
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "duplicate key",
                new SQLException("duplicate key", "23505"),
                "insert into inventory_reservations",
                constraintName
        );
        return new DataIntegrityViolationException("persistence failure", cause);
    }
}
