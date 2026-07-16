package com.agricore.inventory;

import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.HarvestCompletedCommand;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.domain.exception.InventoryException;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL integration against docker-compose Postgres (localhost:5434).
 * Skips cleanly when compose is not running.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
class InventoryPostgresIdempotencyTest {

    private static final String JDBC_URL = "jdbc:postgresql://localhost:5434/agricore_inventory";
    private static final String JDBC_USER = "agricore";
    private static final String JDBC_PASSWORD = "agricore_dev_change_me";

    @BeforeAll
    static void requirePostgres() {
        Assumptions.assumeTrue(isPostgresUp(),
                "Compose Postgres not reachable at localhost:5434 — start scripts/dev-up.ps1");
    }

    private static boolean isPostgresUp() {
        try (var conn = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD)) {
            return conn.isValid(3);
        } catch (Exception ex) {
            return false;
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> JDBC_URL);
        registry.add("spring.datasource.username", () -> JDBC_USER);
        registry.add("spring.datasource.password", () -> JDBC_PASSWORD);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("agricore.security.dev-mode", () -> "true");
        registry.add("agricore.kafka.consumer.enabled", () -> "false");
        registry.add("spring.autoconfigure.exclude",
                () -> "org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration");
    }

    @Autowired
    private InventoryApplicationService inventoryService;

    @Test
    void harvestCompleted_twice_addsStockOnce_onPostgres() {
        var warehouse = inventoryService.createWarehouse(
                new CreateWarehouseRequest("WH-TC-" + System.nanoTime(), "TC Warehouse"));
        String eventId = UUID.randomUUID().toString();

        HarvestCompletedCommand cmd = new HarvestCompletedCommand(
                eventId,
                UUID.randomUUID(),
                warehouse.id(),
                "COFFEE-ROBUSTA",
                new BigDecimal("500.000"),
                "GRADE_A"
        );

        InventoryItemResponse first = inventoryService.processHarvestCompleted(cmd);
        InventoryItemResponse second = inventoryService.processHarvestCompleted(cmd);

        assertThat(first.onHandQuantity()).isEqualByComparingTo("500.000");
        assertThat(second.onHandQuantity()).isEqualByComparingTo("500.000");
        assertThat(first.id()).isEqualTo(second.id());
    }

    @Test
    void concurrentReserve_ofRemainingStock_allowsOnlyOneSuccess() throws Exception {
        var warehouse = inventoryService.createWarehouse(
                new CreateWarehouseRequest("WH-RACE-" + System.nanoTime(), "TC Race WH"));
        var stocked = inventoryService.processHarvestCompleted(new HarvestCompletedCommand(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                warehouse.id(),
                "RICE-ST25",
                new BigDecimal("10.000"),
                "GRADE_A"
        ));

        ExecutorService pool = Executors.newFixedThreadPool(2);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();

        Callable<Void> reserveTask = () -> {
            try {
                inventoryService.reserve(new ReserveStockRequest(
                        stocked.id(),
                        new BigDecimal("10.000"),
                        "SalesOrder",
                        UUID.randomUUID().toString()
                ));
                successes.incrementAndGet();
            } catch (RuntimeException ex) {
                conflicts.incrementAndGet();
            }
            return null;
        };

        List<Future<Void>> futures = new ArrayList<>();
        futures.add(pool.submit(reserveTask));
        futures.add(pool.submit(reserveTask));
        for (Future<Void> f : futures) {
            f.get();
        }
        pool.shutdown();

        InventoryItemResponse after = inventoryService.getItem(stocked.id());
        assertThat(successes.get() + conflicts.get()).isEqualTo(2);
        assertThat(successes.get()).isEqualTo(1);
        assertThat(after.reservedQuantity()).isEqualByComparingTo("10.000");
        assertThat(after.availableQuantity()).isEqualByComparingTo("0.000");
        assertThat(after.onHandQuantity().subtract(after.reservedQuantity()).signum())
                .isGreaterThanOrEqualTo(0);
    }
}
