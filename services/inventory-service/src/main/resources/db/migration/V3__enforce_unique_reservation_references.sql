-- Existing duplicate references are ambiguous stock holds and must be reconciled before upgrade;
-- the unique-index failure is intentionally fail-safe instead of silently deleting inventory data.
CREATE UNIQUE INDEX uk_inventory_reservations_reference
    ON inventory_reservations (reference_type, reference_id);
