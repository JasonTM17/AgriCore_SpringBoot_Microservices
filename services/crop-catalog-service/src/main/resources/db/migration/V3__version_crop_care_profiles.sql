ALTER TABLE crop_growth_requirements
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE crop_growth_requirements
    ADD COLUMN updated_by VARCHAR(255) NOT NULL DEFAULT 'system:migration';
