CREATE TABLE irrigation_zones (
    id                              UUID PRIMARY KEY,
    farm_id                         UUID NOT NULL,
    plot_id                         UUID NOT NULL,
    code                            VARCHAR(64) NOT NULL,
    name                            VARCHAR(200) NOT NULL,
    method                          VARCHAR(32) NOT NULL,
    flow_rate_liters_per_minute     NUMERIC(8, 2) NOT NULL,
    target_moisture_percent         NUMERIC(5, 2) NOT NULL,
    status                          VARCHAR(32) NOT NULL,
    notes                           VARCHAR(1000),
    created_at                      TIMESTAMP NOT NULL,
    updated_at                      TIMESTAMP NOT NULL,
    created_by                      VARCHAR(255) NOT NULL,
    updated_by                      VARCHAR(255) NOT NULL,
    version                         BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_irrigation_zones_plot
        FOREIGN KEY (farm_id, plot_id) REFERENCES plots (farm_id, id),
    CONSTRAINT ck_irrigation_zones_method
        CHECK (method IN (
            'DRIP', 'SPRINKLER', 'MICRO_SPRINKLER', 'CENTER_PIVOT', 'FLOOD', 'MANUAL'
        )),
    CONSTRAINT ck_irrigation_zones_flow_rate
        CHECK (flow_rate_liters_per_minute >= 0.01 AND flow_rate_liters_per_minute <= 999999.99),
    CONSTRAINT ck_irrigation_zones_target_moisture
        CHECK (target_moisture_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_irrigation_zones_status
        CHECK (status IN ('ACTIVE', 'MAINTENANCE', 'INACTIVE'))
);

CREATE UNIQUE INDEX uk_irrigation_zones_plot_code
    ON irrigation_zones (farm_id, plot_id, code);
CREATE INDEX idx_irrigation_zones_plot_status
    ON irrigation_zones (farm_id, plot_id, status);
CREATE INDEX idx_irrigation_zones_plot_method
    ON irrigation_zones (farm_id, plot_id, method);
