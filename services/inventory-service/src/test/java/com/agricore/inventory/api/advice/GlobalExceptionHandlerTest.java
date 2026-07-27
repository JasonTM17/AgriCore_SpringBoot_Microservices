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
                "uk_inventory_reservations_reference",
                "23505"
        );

        var response = handler.dataIntegrity(failure, request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("RESERVATION_REFERENCE_CONFLICT");
        assertThat(response.getBody().message()).doesNotContain("duplicate key");
    }

    @Test
    void unrelatedUniqueConstraintMapsToGenericDuplicateConflict() {
        var response = handler.dataIntegrity(violation("unknown_constraint", "23505"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(409);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DUPLICATE_RESOURCE");
        assertThat(response.getBody().message()).doesNotContain("unknown_constraint");
    }

    @Test
    void nonUniqueConstraintRemainsGenericServerError() {
        var response = handler.dataIntegrity(violation("unknown_constraint", "23502"), request);

        assertThat(response.getStatusCode().value()).isEqualTo(500);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("DATA_INTEGRITY_ERROR");
        assertThat(response.getBody().message()).doesNotContain("unknown_constraint");
    }

    private static DataIntegrityViolationException violation(String constraintName, String sqlState) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "constraint violation",
                new SQLException("constraint violation", sqlState),
                "insert into inventory_reservations",
                constraintName
        );
        return new DataIntegrityViolationException("persistence failure", cause);
    }
}
