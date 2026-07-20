package com.agricore.assistant;

import com.agricore.assistant.application.port.GenerationWorkDispatcher;
import com.agricore.assistant.infrastructure.worker.GenerationWorkCoordinator;
import com.agricore.assistant.infrastructure.worker.GenerationWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = "agricore.assistant.worker.enabled=true")
@ActiveProfiles("test")
class GenerationWorkCoordinatorTransactionIntegrationTest {

    @MockitoBean
    private GenerationWorker worker;

    @Autowired
    private GenerationWorkDispatcher dispatcher;

    @Autowired
    private GenerationWorkCoordinator coordinator;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @BeforeEach
    void completeWorkerImmediately() {
        when(worker.execute(any())).thenReturn(Mono.empty());
    }

    @Test
    void dispatchesOnlyAfterTheSurroundingTransactionCommits() {
        UUID generationId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            dispatcher.dispatchAfterCommit(generationId);
            verifyNoInteractions(worker);
        });

        verify(worker, timeout(1_000)).execute(generationId);
    }

    @Test
    void discardsDispatchWhenTheSurroundingTransactionRollsBack() {
        UUID generationId = UUID.randomUUID();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            dispatcher.dispatchAfterCommit(generationId);
            status.setRollbackOnly();
        });

        verify(worker, after(250).never()).execute(generationId);
    }

    @Test
    void suppressesDuplicateLocalDispatchWhileWorkIsActive() {
        UUID generationId = UUID.randomUUID();
        Sinks.Empty<Void> completion = Sinks.empty();
        when(worker.execute(generationId)).thenReturn(completion.asMono());

        coordinator.dispatch(generationId);
        coordinator.dispatch(generationId);

        verify(worker, timeout(1_000).times(1)).execute(generationId);
        completion.tryEmitEmpty();
    }
}
