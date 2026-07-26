CREATE OR REPLACE FUNCTION lock_crop_cycle_plot_write()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    PERFORM pg_advisory_xact_lock(hashtextextended(NEW.plot_id::text, 0));
    RETURN NEW;
END;
$$;

CREATE TRIGGER trg_crop_cycles_lock_plot_write
    BEFORE INSERT OR UPDATE OF plot_id, planned_start_date, planned_end_date, status
    ON crop_cycles
    FOR EACH ROW
    EXECUTE FUNCTION lock_crop_cycle_plot_write();
