package com.agricore.identity.infrastructure.messaging;

import org.apache.kafka.common.errors.AuthenticationException;
import org.apache.kafka.common.errors.InvalidTopicException;
import org.apache.kafka.common.errors.RecordTooLargeException;
import org.apache.kafka.common.errors.SerializationException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class OutboxPublishFailureTest {

    @Test
    void classifiesOnlyDeterministicProducerFailuresAsPermanent() {
        assertThat(OutboxPublishFailure.from(new SerializationException("serialization")).permanent()).isTrue();
        assertThat(OutboxPublishFailure.from(new RecordTooLargeException("large")).permanent()).isTrue();
        assertThat(OutboxPublishFailure.from(new InvalidTopicException("topic")).permanent()).isTrue();

        assertThat(OutboxPublishFailure.from(new AuthenticationException("auth")).permanent()).isFalse();
        assertThat(OutboxPublishFailure.from(
                new org.apache.kafka.common.errors.TimeoutException("timeout")
        ).permanent()).isFalse();
        assertThat(OutboxPublishFailure.from(new RuntimeException("unknown")).permanent()).isFalse();
    }

    @Test
    void causeTraversalIsBounded() {
        Exception failure = new SerializationException("too deep");
        for (int depth = 0; depth < 16; depth++) {
            failure = new RuntimeException("wrapper", failure);
        }

        assertThat(OutboxPublishFailure.from(failure).permanent()).isFalse();
    }
}
