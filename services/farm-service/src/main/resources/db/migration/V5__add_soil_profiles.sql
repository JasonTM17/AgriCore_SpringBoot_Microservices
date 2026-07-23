ALTER TABLE plots
    ADD CONSTRAINT uk_plots_farm_id_id UNIQUE (farm_id, id);

CREATE TABLE soil_profiles (
    id                          UUID PRIMARY KEY,
    farm_id                     UUID NOT NULL,
    plot_id                     UUID NOT NULL,
    sample_code                 VARCHAR(64) NOT NULL,
    sampled_at                  DATE NOT NULL,
    sample_depth_cm             NUMERIC(5, 2) NOT NULL,
    texture                     VARCHAR(32) NOT NULL,
    ph                          NUMERIC(4, 2) NOT NULL,
    organic_matter_percent      NUMERIC(5, 2),
    nitrogen_mg_kg              NUMERIC(10, 2),
    phosphorus_mg_kg            NUMERIC(10, 2),
    potassium_mg_kg             NUMERIC(10, 2),
    moisture_percent            NUMERIC(5, 2),
    notes                       VARCHAR(1000),
    status                      VARCHAR(32) NOT NULL,
    created_at                  TIMESTAMP NOT NULL,
    updated_at                  TIMESTAMP NOT NULL,
    created_by                  VARCHAR(255) NOT NULL,
    updated_by                  VARCHAR(255) NOT NULL,
    version                     BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_soil_profiles_plot
        FOREIGN KEY (farm_id, plot_id) REFERENCES plots (farm_id, id),
    CONSTRAINT ck_soil_profiles_sample_depth
        CHECK (sample_depth_cm > 0 AND sample_depth_cm <= 999.99),
    CONSTRAINT ck_soil_profiles_ph
        CHECK (ph >= 0 AND ph <= 14),
    CONSTRAINT ck_soil_profiles_texture
        CHECK (texture IN (
            'SAND', 'LOAMY_SAND', 'SANDY_LOAM', 'LOAM', 'SILT_LOAM', 'SILT',
            'SANDY_CLAY_LOAM', 'CLAY_LOAM', 'SILTY_CLAY_LOAM', 'SANDY_CLAY',
            'SILTY_CLAY', 'CLAY'
        )),
    CONSTRAINT ck_soil_profiles_organic_matter
        CHECK (organic_matter_percent IS NULL OR organic_matter_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_soil_profiles_nitrogen
        CHECK (nitrogen_mg_kg IS NULL OR nitrogen_mg_kg >= 0),
    CONSTRAINT ck_soil_profiles_phosphorus
        CHECK (phosphorus_mg_kg IS NULL OR phosphorus_mg_kg >= 0),
    CONSTRAINT ck_soil_profiles_potassium
        CHECK (potassium_mg_kg IS NULL OR potassium_mg_kg >= 0),
    CONSTRAINT ck_soil_profiles_moisture
        CHECK (moisture_percent IS NULL OR moisture_percent BETWEEN 0 AND 100),
    CONSTRAINT ck_soil_profiles_status
        CHECK (status IN ('ACTIVE', 'ARCHIVED'))
);

CREATE UNIQUE INDEX uk_soil_profiles_plot_sample_code
    ON soil_profiles (farm_id, plot_id, sample_code);
CREATE INDEX idx_soil_profiles_plot_sampled_at
    ON soil_profiles (farm_id, plot_id, sampled_at);
CREATE INDEX idx_soil_profiles_plot_status
    ON soil_profiles (farm_id, plot_id, status);
