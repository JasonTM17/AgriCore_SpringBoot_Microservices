CREATE TABLE farm_areas (
    farm_id             UUID NOT NULL REFERENCES farms(id),
    id                  UUID NOT NULL,
    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(200) NOT NULL,
    area_in_hectares    NUMERIC(14, 4),
    description         VARCHAR(500),
    status              VARCHAR(32) NOT NULL,
    created_at          TIMESTAMP NOT NULL,
    updated_at          TIMESTAMP NOT NULL,
    created_by          VARCHAR(255) NOT NULL,
    updated_by          VARCHAR(255) NOT NULL,
    version             BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (farm_id, id)
);

CREATE UNIQUE INDEX uk_farm_areas_farm_code ON farm_areas (farm_id, code);
CREATE INDEX idx_farm_areas_farm_status ON farm_areas (farm_id, status);

INSERT INTO farm_areas (
    farm_id,
    id,
    code,
    name,
    area_in_hectares,
    description,
    status,
    created_at,
    updated_at,
    created_by,
    updated_by,
    version
)
SELECT
    p.farm_id,
    p.area_id,
    CONCAT('LEGACY-', CAST(p.area_id AS VARCHAR(36))),
    CONCAT('Imported area ', CAST(p.area_id AS VARCHAR(36))),
    CAST(NULL AS NUMERIC(14, 4)),
    'Imported from plot assignments created before Farm Area management',
    'ACTIVE',
    MIN(p.created_at),
    MAX(p.updated_at),
    'migration-v4',
    'migration-v4',
    0
FROM plots p
WHERE p.area_id IS NOT NULL
GROUP BY p.farm_id, p.area_id;

ALTER TABLE plots
    ADD CONSTRAINT fk_plots_farm_area
    FOREIGN KEY (farm_id, area_id)
    REFERENCES farm_areas (farm_id, id);

CREATE INDEX idx_plots_farm_area ON plots (farm_id, area_id);
