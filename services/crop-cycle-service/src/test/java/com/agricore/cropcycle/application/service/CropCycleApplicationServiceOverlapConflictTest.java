package com.agricore.cropcycle.application.service;

import com.agricore.cropcycle.api.request.CreateCropCycleRequest;
import com.agricore.cropcycle.domain.exception.CropCycleException;
import com.agricore.cropcycle.infrastructure.persistence.CropCycleJpaRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CropCycleApplicationServiceOverlapConflictTest {

    @Test
    void exactDatabaseOverlapConstraintMapsToDomainConflict() {
        CropCycleJpaRepository repository = repositoryThrowing(
                violation(CropCycleApplicationService.ACTIVE_CYCLE_OVERLAP_CONSTRAINT)
        );
        CropCycleApplicationService service = service(repository);

        assertThatThrownBy(() -> service.create(request(), "postgres-overlap-test"))
                .isInstanceOfSatisfying(CropCycleException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo("CROP_CYCLE_OVERLAP");
                    assertThat(exception.getHttpStatus()).isEqualTo(409);
                });
    }

    @Test
    void unrelatedDatabaseConstraintIsNotMisreportedAsOverlap() {
        DataIntegrityViolationException unrelated = violation("uk_crop_cycles_code");
        CropCycleApplicationService service = service(repositoryThrowing(unrelated));

        assertThatThrownBy(() -> service.create(request(), "postgres-overlap-test"))
                .isSameAs(unrelated);
    }

    private static CropCycleApplicationService service(CropCycleJpaRepository repository) {
        return new CropCycleApplicationService(
                repository,
                mock(CropCycleOutboxWriter.class),
                mock(CropCycleAccessGuard.class),
                mock(CropCycleStageHistoryService.class)
        );
    }

    private static CropCycleJpaRepository repositoryThrowing(DataIntegrityViolationException failure) {
        CropCycleJpaRepository repository = mock(CropCycleJpaRepository.class);
        when(repository.existsByCodeIgnoreCase(anyString())).thenReturn(false);
        when(repository.findOverlappingActiveCycles(
                any(UUID.class),
                anyCollection(),
                any(LocalDate.class),
                any(LocalDate.class),
                any(LocalDate.class)
        )).thenReturn(List.of());
        when(repository.saveAndFlush(any())).thenThrow(failure);
        return repository;
    }

    private static CreateCropCycleRequest request() {
        return new CreateCropCycleRequest(
                "CC-CONSTRAINT-" + System.nanoTime(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                LocalDate.of(2026, 1, 1),
                LocalDate.of(2026, 12, 31),
                null
        );
    }

    private static DataIntegrityViolationException violation(String constraintName) {
        ConstraintViolationException cause = new ConstraintViolationException(
                "conflicting key",
                new SQLException("conflicting key", "23P01"),
                "insert into crop_cycles",
                constraintName
        );
        return new DataIntegrityViolationException("persistence failure", cause);
    }
}
