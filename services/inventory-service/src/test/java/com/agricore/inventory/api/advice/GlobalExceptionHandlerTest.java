package com.agricore.inventory.api.advice;

import jakarta.persistence.OptimisticLockException;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.orm.jpa.JpaOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

    @Test
    void optimisticLockFailureReturnsActionableConflict() throws Exception {
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ConflictController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        mockMvc.perform(post("/test/optimistic-lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("OPTIMISTIC_LOCK"))
                .andExpect(jsonPath("$.message").value("Concurrent stock update conflict; retry the request"))
                .andExpect(jsonPath("$.path").value("/test/optimistic-lock"));
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

    @RestController
    static class ConflictController {

        @PostMapping("/test/optimistic-lock")
        void conflict() {
            throw new JpaOptimisticLockingFailureException(new OptimisticLockException("stale version"));
        }
    }
}
