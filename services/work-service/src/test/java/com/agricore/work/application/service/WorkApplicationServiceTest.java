package com.agricore.work.application.service;

import com.agricore.work.api.request.CreateWorkTaskRequest;
import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class WorkApplicationServiceTest {

    @Test
    void completeRejectsInvalidActorBeforeDelegatingMaterialSideEffects() {
        WorkTaskCompletionService completionService = mock(WorkTaskCompletionService.class);
        WorkApplicationService service = new WorkApplicationService(
                mock(WorkTaskJpaRepository.class),
                mock(WorkAccessGuard.class),
                mock(WorkEventOutboxWriter.class),
                mock(WorkTaskLifecycleService.class),
                completionService,
                mock(WorkTaskResponseAssembler.class)
        );

        assertThatThrownBy(() -> service.complete(
                UUID.randomUUID(),
                new CompleteTaskRequest(null),
                "x".repeat(256)
        ))
                .isInstanceOf(WorkException.class)
                .extracting("code")
                .isEqualTo("INVALID_ACTOR");
        verifyNoInteractions(completionService);
    }

    @Test
    void createMapsTheAuthoritativeTaskCodeConstraintToConflict() {
        WorkTaskJpaRepository repository = mock(WorkTaskJpaRepository.class);
        WorkEventOutboxWriter eventWriter = mock(WorkEventOutboxWriter.class);
        WorkAccessGuard accessGuard = mock(WorkAccessGuard.class);
        when(repository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException(
                "duplicate key violates unique constraint uk_work_tasks_code"
        ));
        WorkApplicationService service = new WorkApplicationService(
                repository,
                accessGuard,
                eventWriter,
                mock(WorkTaskLifecycleService.class),
                mock(WorkTaskCompletionService.class),
                mock(WorkTaskResponseAssembler.class)
        );

        assertThatThrownBy(() -> service.create(new CreateWorkTaskRequest(
                "task-race",
                UUID.randomUUID(),
                UUID.randomUUID(),
                "IRRIGATION",
                "Concurrent irrigation",
                null,
                "HIGH",
                null,
                null
        )))
                .isInstanceOf(WorkException.class)
                .hasMessage("Task code already exists")
                .extracting("code", "httpStatus")
                .containsExactly("TASK_CODE_EXISTS", 409);
        verifyNoInteractions(eventWriter);
    }
}
