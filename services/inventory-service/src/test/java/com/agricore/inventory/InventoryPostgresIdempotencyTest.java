package com.agricore.inventory;

import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.HarvestCompletedCommand;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.domain.exception.InventoryException;
import com.agricore.inventory.infrastructure.persistence.InventoryReservationJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Real PostgreSQL integration:
 * 1) Prefer compose Postgres on 127.0.0.1:5434 (stable for full-stack verify)
 * 2) Else Testcontainers if Docker API is usable
 * Never silent-skip — either executes 2 tests or fails class init.
 */
@SpringBootTest
@ActiveProfiles("testcontainers")
class InventoryPostgresIdempotencyTest {

    private static final Logger log = LoggerFactory.getLogger(InventoryPostgresIdempotencyTest.class);

    // Use 127.0.0.1 — Windows localhost may resolve to ::1 while Docker publishes IPv4 only
    private static final String COMPOSE_JDBC = "jdbc:postgresql://127.0.0.1:5434/agricore_inventory";
    private static final String COMPOSE_USER = "agricore";
    private static final String COMPOSE_PASSWORD = "agricore_dev_change_me";

    private static PostgreSQLContainer<?> container;
    private static String jdbcUrl;
    private static String username;
    private static String password;

    static {
        if (isComposePostgresUp()) {
            jdbcUrl = COMPOSE_JDBC;
            username = COMPOSE_USER;
            password = COMPOSE_PASSWORD;
            log.info("Using compose Postgres at {}", jdbcUrl);
        } else if (tryStartTestcontainers()) {
            jdbcUrl = container.getJdbcUrl();
            username = container.getUsername();
            password = container.getPassword();
            log.info("Using Testcontainers Postgres at {}", jdbcUrl);
        } else {
            throw new IllegalStateException(
                    "PostgreSQL required for InventoryPostgresIdempotencyTest: start Docker Desktop "
                            + "(Testcontainers) or `docker compose up -d postgres` (127.0.0.1:5434)");
        }
    }

    private static boolean tryStartTestcontainers() {
        try {
            DockerClientFactory.instance().client();
            container = new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("agricore_inventory")
                    .withUsername(COMPOSE_USER)
                    .withPassword(COMPOSE_PASSWORD);
            container.start();
            return true;
        } catch (Throwable ex) {
            log.warn("Testcontainers unavailable: {}", ex.toString());
            container = null;
            return false;
        }
    }

    private static boolean isComposePostgresUp() {
        try {
            Class.forName("org.postgresql.Driver");
            for (int attempt = 1; attempt <= 10; attempt++) {
                try (var conn = DriverManager.getConnection(COMPOSE_JDBC, COMPOSE_USER, COMPOSE_PASSWORD)) {
                    if (conn.isValid(5)) {
                        return true;
                    }
                } catch (Exception ex) {
                    log.warn("Compose Postgres attempt {}/10 failed: {}", attempt, ex.toString());
                    try {
                        Thread.sleep(500L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return false;
                    }
                }
            }
            return false;
        } catch (ClassNotFoundException ex) {
            log.warn("PostgreSQL JDBC driver missing: {}", ex.toString());
            return false;
        }
    }

    @AfterAll
    static void stopContainer() {
        if (container != null) {
            container.stop();
        }
    }

    @DynamicPropertySource
    static void datasourceProps(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", () -> username);
        registry.add("spring.datasource.password", () -> password);
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

    @Autowired
    private InventoryReservationJpaRepository reservationRepository;

    @Autowired
    private MeterRegistry meterRegistry;

    @Test
    void harvestCompleted_twice_addsStockOnce_onPostgres() {
        var warehouse = inventoryService.createWarehouse(
                new CreateWarehouseRequest(
                        UUID.randomUUID(), "WH-TC-" + System.nanoTime(), "TC Warehouse"
                ));
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
                new CreateWarehouseRequest(
                        UUID.randomUUID(), "WH-RACE-" + System.nanoTime(), "TC Race WH"
                ));
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

    @Test
    void concurrentSameReferenceForDifferentItemsCommitsOneReservationAndOneMetric() throws Exception {
        var warehouse = inventoryService.createWarehouse(
                new CreateWarehouseRequest(
                        UUID.randomUUID(), "WH-REF-RACE-" + System.nanoTime(), "Reference Race WH"
                ));
        InventoryItemResponse firstItem = stockedItem(warehouse.id(), "REF-RACE-A");
        InventoryItemResponse secondItem = stockedItem(warehouse.id(), "REF-RACE-B");
        String referenceId = UUID.randomUUID().toString();
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger successes = new AtomicInteger();
        AtomicInteger conflicts = new AtomicInteger();
        double successMetricBefore = reservationSuccessMetric();
        ExecutorService pool = Executors.newFixedThreadPool(2);

        Callable<Void> first = reserveWithReference(
                firstItem.id(), referenceId, start, successes, conflicts);
        Callable<Void> second = reserveWithReference(
                secondItem.id(), referenceId, start, successes, conflicts);

        try {
            Future<Void> firstResult = pool.submit(first);
            Future<Void> secondResult = pool.submit(second);
            start.countDown();
            firstResult.get();
            secondResult.get();
        } finally {
            pool.shutdownNow();
        }

        assertThat(successes.get()).isEqualTo(1);
        assertThat(conflicts.get()).isEqualTo(1);
        assertThat(reservationRepository.findByReferenceTypeAndReferenceId("SalesOrder", referenceId))
                .isPresent();
        BigDecimal totalReserved = inventoryService.getItem(firstItem.id()).reservedQuantity()
                .add(inventoryService.getItem(secondItem.id()).reservedQuantity());
        assertThat(totalReserved).isEqualByComparingTo("5.000");
        assertThat(reservationSuccessMetric() - successMetricBefore).isEqualTo(1.0d);
    }

    private InventoryItemResponse stockedItem(UUID warehouseId, String productCode) {
        return inventoryService.processHarvestCompleted(new HarvestCompletedCommand(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                warehouseId,
                productCode,
                new BigDecimal("20.000"),
                "GRADE_A"
        ));
    }

    private Callable<Void> reserveWithReference(
            UUID itemId,
            String referenceId,
            CountDownLatch start,
            AtomicInteger successes,
            AtomicInteger conflicts
    ) {
        return () -> {
            start.await();
            try {
                inventoryService.reserve(new ReserveStockRequest(
                        itemId,
                        new BigDecimal("5.000"),
                        "SalesOrder",
                        referenceId
                ));
                successes.incrementAndGet();
            } catch (RuntimeException exception) {
                boolean referenceConflict = exception instanceof DataIntegrityViolationException
                        || exception instanceof InventoryException inventoryException
                        && "RESERVATION_REFERENCE_CONFLICT".equals(inventoryException.getCode());
                if (!referenceConflict) {
                    throw exception;
                }
                conflicts.incrementAndGet();
            }
            return null;
        };
    }

    private double reservationSuccessMetric() {
        return meterRegistry.get("agricore.inventory.reservations")
                .tag("outcome", "success")
                .counter()
                .count();
    }
}
