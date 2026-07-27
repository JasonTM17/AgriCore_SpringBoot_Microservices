package com.agricore.iot;

import com.agricore.iot.api.request.IngestReadingRequest;
import com.agricore.iot.application.service.IotReadingIngestionService;
import com.agricore.iot.domain.exception.IotException;
import com.agricore.iot.infrastructure.persistence.DeviceJpaRepository;
import com.agricore.iot.infrastructure.persistence.SensorReadingJpaRepository;
import com.agricore.iot.infrastructure.persistence.entity.DeviceEntity;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.math.BigDecimal;
import java.sql.DriverManager;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
class IotTimescaleIntegrationTest {

    private static final DockerImageName TIMESCALE_IMAGE = DockerImageName.parse(
                    "timescale/timescaledb:2.27.0-pg16"
                            + "@sha256:51eb3bcdfc41f481c797026813d9d457fb5cbc8ea370a65640d8cda13a4040c1")
            .asCompatibleSubstituteFor("postgres");
    private static final PostgreSQLContainer<?> TIMESCALE = new PostgreSQLContainer<>(TIMESCALE_IMAGE)
            .withDatabaseName("agricore_iot")
            .withUsername("agricore")
            .withPassword("agricore-test");
    private static final UUID LEGACY_DEVICE_ID = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private static final UUID LEGACY_READING_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private static final Instant LEGACY_RECORDED_AT = Instant.parse("2026-07-01T12:00:00Z");

    @Autowired
    private JdbcTemplate jdbcTemplate;
    @Autowired
    private DeviceJpaRepository deviceRepository;
    @Autowired
    private SensorReadingJpaRepository readingRepository;
    @Autowired
    private IotReadingIngestionService ingestionService;

    @DynamicPropertySource
    static void configureTimescale(DynamicPropertyRegistry registry) {
        TIMESCALE.start();
        migrateLegacySchemaAndSeedReading();
        registry.add("spring.datasource.url", TIMESCALE::getJdbcUrl);
        registry.add("spring.datasource.username", TIMESCALE::getUsername);
        registry.add("spring.datasource.password", TIMESCALE::getPassword);
        registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        registry.add("spring.jpa.properties.hibernate.dialect",
                () -> "org.hibernate.dialect.PostgreSQLDialect");
    }

    @Test
    void migrationBackfillsLedgerAndCreatesSevenDayHypertableWithoutRetention() {
        assertThat(jdbcTemplate.queryForObject(
                "SELECT extversion FROM pg_extension WHERE extname = 'timescaledb'", String.class))
                .isEqualTo("2.27.0");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM timescaledb_information.hypertables
                WHERE hypertable_schema = 'public' AND hypertable_name = 'sensor_readings'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT time_interval::text
                FROM timescaledb_information.dimensions
                WHERE hypertable_schema = 'public'
                  AND hypertable_name = 'sensor_readings'
                  AND column_name = 'recorded_at'
                """, String.class)).isEqualTo("7 days");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM sensor_reading_idempotency
                WHERE reading_id = ?
                  AND device_id = ?
                  AND recorded_at = ?
                """, Integer.class, LEGACY_READING_ID, LEGACY_DEVICE_ID,
                OffsetDateTime.ofInstant(LEGACY_RECORDED_AT, ZoneOffset.UTC))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT pg_get_constraintdef(oid)
                FROM pg_constraint
                WHERE conrelid = 'sensor_readings'::regclass AND contype = 'p'
                """, String.class)).isEqualTo("PRIMARY KEY (id, recorded_at)");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND tablename = 'sensor_readings'
                  AND indexname = 'idx_sensor_readings_device_metric_time'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*)
                FROM timescaledb_information.jobs
                WHERE proc_name = 'policy_retention'
                  AND hypertable_schema = 'public'
                  AND hypertable_name = 'sensor_readings'
                """, Integer.class)).isZero();
    }

    @Test
    void exactReplayIsIgnoredAndConflictingReplayIsRejectedOnTimescale() {
        DeviceEntity device = registerDevice("TS-" + UUID.randomUUID());
        UUID readingId = UUID.randomUUID();
        Instant recordedAt = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);
        var request = reading(readingId, device.getDeviceCode(), "50.0000", recordedAt);

        assertThat(ingestionService.ingest(request, device.getId()).message()).isEqualTo("Reading accepted");
        assertThat(ingestionService.ingest(request, device.getId()).message())
                .isEqualTo("Duplicate reading ignored");
        assertThat(readingRepository.findById(readingId)).isPresent();
        assertThatThrownBy(() -> ingestionService.ingest(
                reading(readingId, device.getDeviceCode(), "49.0000", recordedAt), device.getId()))
                .isInstanceOf(IotException.class)
                .extracting("code")
                .isEqualTo("READING_ID_CONFLICT");
    }

    @Test
    void concurrentClaimsKeepReadingIdsGlobalWithoutBlockingDifferentIds() throws Exception {
        DeviceEntity firstDevice = registerDevice("TS-CONCURRENT-A-" + UUID.randomUUID());
        DeviceEntity secondDevice = registerDevice("TS-CONCURRENT-B-" + UUID.randomUUID());
        Instant recordedAt = Instant.now().minusSeconds(10).truncatedTo(ChronoUnit.MICROS);

        UUID sharedReadingId = UUID.randomUUID();
        List<String> sharedIdOutcomes = runConcurrently(
                claim(sharedReadingId, firstDevice, recordedAt),
                claim(sharedReadingId, secondDevice, recordedAt)
        );
        assertThat(sharedIdOutcomes).containsExactlyInAnyOrder("Reading accepted", "READING_ID_CONFLICT");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT count(*) FROM sensor_reading_idempotency WHERE reading_id = ?
                """, Integer.class, sharedReadingId)).isEqualTo(1);

        List<String> differentIdOutcomes = runConcurrently(
                claim(UUID.randomUUID(), firstDevice, recordedAt),
                claim(UUID.randomUUID(), secondDevice, recordedAt)
        );
        assertThat(differentIdOutcomes).containsOnly("Reading accepted");
    }

    private Callable<String> claim(UUID readingId, DeviceEntity device, Instant recordedAt) {
        return () -> {
            try {
                return ingestionService.ingest(
                        reading(readingId, device.getDeviceCode(), "50.0000", recordedAt),
                        device.getId()
                ).message();
            } catch (IotException exception) {
                return exception.getCode();
            }
        };
    }

    private static List<String> runConcurrently(
            Callable<String> firstClaim,
            Callable<String> secondClaim
    ) throws Exception {
        var start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> {
                start.await();
                return firstClaim.call();
            });
            var second = executor.submit(() -> {
                start.await();
                return secondClaim.call();
            });
            start.countDown();
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            executor.shutdownNow();
        }
    }

    private DeviceEntity registerDevice(String deviceCode) {
        DeviceEntity device = new DeviceEntity();
        device.setId(UUID.randomUUID());
        device.setDeviceCode(deviceCode);
        device.setPlotId(UUID.randomUUID());
        device.setName("Timescale integration probe");
        device.setStatus("ACTIVE");
        device.setCreatedAt(Instant.now());
        return deviceRepository.save(device);
    }

    private static IngestReadingRequest reading(
            UUID readingId,
            String deviceCode,
            String metricValue,
            Instant recordedAt
    ) {
        return new IngestReadingRequest(
                deviceCode,
                "SOIL_MOISTURE",
                new BigDecimal(metricValue),
                "PERCENT",
                recordedAt,
                readingId
        );
    }

    private static void migrateLegacySchemaAndSeedReading() {
        Flyway.configure()
                .dataSource(TIMESCALE.getJdbcUrl(), TIMESCALE.getUsername(), TIMESCALE.getPassword())
                .locations("classpath:db/migration/common", "classpath:db/migration/postgresql")
                .target("3")
                .load()
                .migrate();
        try (var connection = DriverManager.getConnection(
                TIMESCALE.getJdbcUrl(), TIMESCALE.getUsername(), TIMESCALE.getPassword())) {
            try (var statement = connection.prepareStatement("""
                    INSERT INTO devices (
                        id, device_code, plot_id, name, status, created_at, version
                    ) VALUES (
                        ?, 'LEGACY-TS-001', ?, 'Legacy probe', 'ACTIVE',
                        TIMESTAMP '2026-07-01 12:00:00', 0
                    )
                    """)) {
                statement.setObject(1, LEGACY_DEVICE_ID);
                statement.setObject(2, UUID.fromString("30000000-0000-0000-0000-000000000001"));
                statement.executeUpdate();
            }
            try (var statement = connection.prepareStatement("""
                    INSERT INTO sensor_readings (
                        id, device_id, metric_type, metric_value, unit, recorded_at, created_at
                    ) VALUES (
                        ?, ?, 'SOIL_MOISTURE', 42.5000, 'PERCENT',
                        TIMESTAMP '2026-07-01 12:00:00',
                        TIMESTAMP '2026-07-01 12:00:00'
                    )
                    """)) {
                statement.setObject(1, LEGACY_READING_ID);
                statement.setObject(2, LEGACY_DEVICE_ID);
                statement.executeUpdate();
            }
        } catch (Exception exception) {
            throw new IllegalStateException("Could not prepare legacy IoT schema", exception);
        }
    }
}
