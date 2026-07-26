package com.agricore.inventory;

import com.agricore.common.persistence.ConstraintViolations;
import com.agricore.inventory.api.request.CreateWarehouseRequest;
import com.agricore.inventory.api.request.HarvestCompletedCommand;
import com.agricore.inventory.api.request.ReserveStockRequest;
import com.agricore.inventory.api.response.InventoryItemResponse;
import com.agricore.inventory.application.service.InventoryApplicationService;
import com.agricore.inventory.infrastructure.persistence.WarehouseJpaRepository;
import com.agricore.inventory.infrastructure.persistence.entity.WarehouseEntity;
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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

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
    private WarehouseJpaRepository warehouseRepository;

    /**
     * Pins the SQLState the duplicate-key handler keys on against a real PostgreSQL server.
     *
     * <p>The handler classifies by SQLState rather than by exception subtype, because Hibernate's
     * JPA dialect collapses every class 23 failure into one {@code DataIntegrityViolationException}
     * and never narrows it to {@code DuplicateKeyException}. Every other test in the suite runs on
     * H2 in PostgreSQL mode, which is not proof of what PostgreSQL itself reports — so this asserts
     * it directly.
     *
     * <p>Goes through the repository rather than the application service on purpose: the service
     * checks for an existing code first, and that check is exactly what a concurrent request slips
     * past. Bypassing it reproduces the losing side of that race deterministically.
     */
    @Test
    void duplicateWarehouseCode_reportsUniqueViolationSqlState_onPostgres() {
        String sharedCode = "WH-DUP-" + System.nanoTime();
        warehouseRepository.saveAndFlush(warehouse(sharedCode));

        DataIntegrityViolationException thrown = catchThrowableOfType(
                () -> warehouseRepository.saveAndFlush(warehouse(sharedCode)),
                DataIntegrityViolationException.class
        );

        assertThat(thrown).as("uk_warehouses_code must reject the second insert").isNotNull();
        assertThat(ConstraintViolations.sqlState(thrown))
                .as("PostgreSQL reports unique_violation as SQLState 23505")
                .isEqualTo("23505");
        assertThat(ConstraintViolations.isUniqueViolation(thrown)).isTrue();
        assertThat(ConstraintViolations.isForeignKeyViolation(thrown)).isFalse();
        assertThat(ConstraintViolations.isNotNullViolation(thrown)).isFalse();
    }

    /**
     * The other half of the classification: a missing required value must not be reported as a
     * duplicate, or the handler would answer "already exists" to a service-side defect.
     */
    @Test
    void missingRequiredColumn_isNotReportedAsDuplicate_onPostgres() {
        WarehouseEntity noName = warehouse("WH-NULL-" + System.nanoTime());
        noName.setName(null);

        DataIntegrityViolationException thrown = catchThrowableOfType(
                () -> warehouseRepository.saveAndFlush(noName),
                DataIntegrityViolationException.class
        );

        assertThat(thrown).isNotNull();
        assertThat(ConstraintViolations.isUniqueViolation(thrown))
                .as("a not-null violation is a server fault, not a caller conflict")
                .isFalse();
    }

    private static WarehouseEntity warehouse(String code) {
        WarehouseEntity entity = new WarehouseEntity();
        entity.setId(UUID.randomUUID());
        entity.setCode(code);
        entity.setName("Constraint Probe WH");
        entity.setCreatedAt(Instant.now());
        return entity;
    }

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
