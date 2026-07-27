ALTER TABLE harvest_batches
    ADD CONSTRAINT ck_harvest_weight_order
        CHECK (
            (gross_weight_kg IS NULL AND net_weight_kg IS NULL)
            OR (
                gross_weight_kg > 0
                AND net_weight_kg > 0
                AND net_weight_kg <= gross_weight_kg
            )
        );

ALTER TABLE harvest_batches
    ADD CONSTRAINT ck_harvest_status
        CHECK (status IN ('IN_PROGRESS', 'RECORDED', 'COMPLETED', 'CANCELLED'));

ALTER TABLE harvest_batches
    ADD CONSTRAINT ck_harvest_completed_fields
        CHECK (
            status = 'IN_PROGRESS'
            OR (
                gross_weight_kg IS NOT NULL
                AND net_weight_kg IS NOT NULL
                AND quality_grade IS NOT NULL
            )
        );
