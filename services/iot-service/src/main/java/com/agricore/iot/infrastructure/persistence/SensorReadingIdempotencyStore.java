package com.agricore.iot.infrastructure.persistence;

import com.agricore.iot.infrastructure.persistence.entity.SensorReadingIdempotencyEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.jdbc.support.MetaDataAccessException;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@Component
public class SensorReadingIdempotencyStore {

    private static final String INSERT = """
            INSERT INTO sensor_reading_idempotency (
                reading_id, device_id, metric_type, metric_value, unit, recorded_at, created_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final SensorReadingIdempotencyJpaRepository repository;
    private final boolean postgres;

    public SensorReadingIdempotencyStore(
            JdbcTemplate jdbcTemplate,
            SensorReadingIdempotencyJpaRepository repository,
            DataSource dataSource
    ) throws MetaDataAccessException {
        this.jdbcTemplate = jdbcTemplate;
        this.repository = repository;
        String productName = JdbcUtils.extractDatabaseMetaData(
                dataSource,
                metadata -> metadata.getDatabaseProductName()
        );
        this.postgres = productName != null && productName.contains("PostgreSQL");
    }

    public Optional<SensorReadingIdempotencyEntity> claim(
            UUID readingId,
            UUID deviceId,
            String metricType,
            BigDecimal metricValue,
            String unit,
            Instant recordedAt,
            Instant createdAt
    ) {
        Object[] values = {
                readingId,
                deviceId,
                metricType,
                metricValue,
                unit,
                OffsetDateTime.ofInstant(recordedAt, ZoneOffset.UTC),
                OffsetDateTime.ofInstant(createdAt, ZoneOffset.UTC)
        };
        if (postgres) {
            int inserted = jdbcTemplate.update(INSERT + " ON CONFLICT (reading_id) DO NOTHING", values);
            return inserted == 1 ? Optional.empty() : findRequired(readingId);
        }

        Optional<SensorReadingIdempotencyEntity> existing = repository.findById(readingId);
        if (existing.isPresent()) {
            return existing;
        }
        jdbcTemplate.update(INSERT, values);
        return Optional.empty();
    }

    private Optional<SensorReadingIdempotencyEntity> findRequired(UUID readingId) {
        Optional<SensorReadingIdempotencyEntity> existing = repository.findById(readingId);
        if (existing.isEmpty()) {
            throw new IllegalStateException("Reading idempotency claim disappeared");
        }
        return existing;
    }
}
