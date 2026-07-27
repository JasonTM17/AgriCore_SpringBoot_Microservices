package com.agricore.inventory;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class InventoryOutboxRetryMigrationContractTest {

    @Test
    void postgresIndexesAreConcurrentAndNonTransactional() throws IOException {
        assertConcurrentIndex(
                "db/postgresql-migration/V9__index_inventory_outbox_retry_queue.sql",
                "idx_inventory_outbox_retry_queue",
                "published_at IS NULL AND quarantined_at IS NULL"
        );
        assertConcurrentIndex(
                "db/postgresql-migration/V10__index_inventory_outbox_quarantine.sql",
                "idx_inventory_outbox_quarantine",
                "quarantined_at IS NOT NULL"
        );
    }

    private static void assertConcurrentIndex(
            String resource,
            String indexName,
            String predicate
    ) throws IOException {
        assertThat(resourceText(resource))
                .contains("CREATE INDEX CONCURRENTLY " + indexName)
                .contains(predicate);
        assertThat(resourceText(resource + ".conf")).contains("executeInTransaction=false");
    }

    private static String resourceText(String resource) throws IOException {
        try (var stream = InventoryOutboxRetryMigrationContractTest.class
                .getClassLoader()
                .getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
