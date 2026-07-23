package com.agricore.work.application.service;

import com.agricore.work.api.request.CompleteTaskRequest;
import com.agricore.work.domain.exception.WorkException;
import com.agricore.work.infrastructure.persistence.WorkTaskJpaRepository;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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
}
