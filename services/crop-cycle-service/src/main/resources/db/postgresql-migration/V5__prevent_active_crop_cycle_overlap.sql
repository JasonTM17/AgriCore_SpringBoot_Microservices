CREATE EXTENSION IF NOT EXISTS btree_gist;

ALTER TABLE crop_cycles
    ADD CONSTRAINT crop_cycles_no_active_date_overlap
    EXCLUDE USING gist (
        plot_id WITH =,
        daterange(planned_start_date, planned_end_date, '[]') WITH &&
    )
    WHERE (status IN ('DRAFT', 'ACTIVE'));
