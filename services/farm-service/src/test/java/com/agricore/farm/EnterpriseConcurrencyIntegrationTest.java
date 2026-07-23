package com.agricore.farm;

import com.agricore.farm.infrastructure.persistence.EnterpriseJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class EnterpriseConcurrencyIntegrationTest extends EnterpriseApiTestSupport {

    @Autowired
    private EnterpriseJpaRepository enterpriseRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    void concurrentEnterpriseUpdatesUseOptimisticLocking() throws Exception {
        JsonNode created = createEnterprise("ENT-CONCURRENT", "Concurrent", "TAX-CONCURRENT");
        UUID enterpriseId = UUID.fromString(created.get("id").asText());
        CountDownLatch loaded = new CountDownLatch(2);
        CountDownLatch write = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<String>> futures = List.of(
                    executor.submit(() -> updateName(enterpriseId, "First", loaded, write)),
                    executor.submit(() -> updateName(enterpriseId, "Second", loaded, write))
            );
            assertTrue(loaded.await(5, TimeUnit.SECONDS));
            write.countDown();
            List<String> outcomes = futures.stream()
                    .map(EnterpriseConcurrencyIntegrationTest::result)
                    .sorted()
                    .toList();
            assertEquals(List.of("OPTIMISTIC_LOCK", "UPDATED"), outcomes);
        } finally {
            executor.shutdownNow();
        }

        var persisted = enterpriseRepository.findById(enterpriseId).orElseThrow();
        assertEquals(1, persisted.getVersion());
        assertTrue(List.of("First", "Second").contains(persisted.getName()));
    }

    private String updateName(
            UUID enterpriseId,
            String name,
            CountDownLatch loaded,
            CountDownLatch write
    ) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                var enterprise = enterpriseRepository.findById(enterpriseId).orElseThrow();
                loaded.countDown();
                await(write);
                enterprise.setName(name);
                enterpriseRepository.saveAndFlush(enterprise);
            });
            return "UPDATED";
        } catch (ObjectOptimisticLockingFailureException ex) {
            return "OPTIMISTIC_LOCK";
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Concurrent enterprise update timed out");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Concurrent enterprise update interrupted", ex);
        }
    }

    private static String result(Future<String> future) {
        try {
            return future.get(10, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new AssertionError("Concurrent enterprise update failed", ex);
        }
    }
}
