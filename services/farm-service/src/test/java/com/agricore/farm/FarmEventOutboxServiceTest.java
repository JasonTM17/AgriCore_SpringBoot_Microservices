package com.agricore.farm;

import com.agricore.farm.application.service.FarmEventOutboxService;
import com.agricore.farm.domain.exception.FarmException;
import com.agricore.farm.infrastructure.persistence.OutboxJpaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class FarmEventOutboxServiceTest {

    @Test
    void serializationFailurePreservesCauseAndSkipsPersistence() throws Exception {
        OutboxJpaRepository repository = mock(OutboxJpaRepository.class);
        ObjectMapper objectMapper = mock(ObjectMapper.class);
        JsonProcessingException cause = new JsonProcessingException("serialization failed") { };
        when(objectMapper.createObjectNode()).thenReturn(JsonNodeFactory.instance.objectNode());
        when(objectMapper.writeValueAsString(any())).thenThrow(cause);
        FarmEventOutboxService service = new FarmEventOutboxService(repository, objectMapper);

        FarmException exception = assertThrows(
                FarmException.class,
                () -> service.enqueue(
                        "Farm",
                        "farm-1",
                        "FarmCreated.v1",
                        "agricore.farm.events",
                        JsonNodeFactory.instance.objectNode()
                )
        );

        assertEquals("OUTBOX_WRITE_FAILED", exception.getCode());
        assertSame(cause, exception.getCause());
        verifyNoInteractions(repository);
    }
}
