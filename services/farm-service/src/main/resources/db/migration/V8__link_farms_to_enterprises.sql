ALTER TABLE farms ADD COLUMN enterprise_id UUID;

ALTER TABLE farms
    ADD CONSTRAINT fk_farms_enterprise
    FOREIGN KEY (enterprise_id) REFERENCES enterprises(id);

CREATE INDEX idx_farms_enterprise_id ON farms (enterprise_id);
