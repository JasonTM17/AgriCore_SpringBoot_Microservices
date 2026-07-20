-- V1 stored immutable role snapshots as comma-delimited text. Keep V2's
-- checksum stable and canonicalize rows after both old and new V2 upgrades.
UPDATE conversations
SET role_snapshot = CASE
    WHEN TRIM(role_snapshot) = '' THEN '[]'
    WHEN SUBSTRING(TRIM(role_snapshot), 1, 1) = '[' THEN TRIM(role_snapshot)
    ELSE '["' || REPLACE(REPLACE(TRIM(role_snapshot), ', ', ','), ',', '","') || '"]'
END
WHERE role_snapshot IS NOT NULL
  AND (TRIM(role_snapshot) = '' OR SUBSTRING(TRIM(role_snapshot), 1, 1) <> '[');

UPDATE chat_generations
SET role_snapshot = CASE
    WHEN TRIM(role_snapshot) = '' THEN '[]'
    WHEN SUBSTRING(TRIM(role_snapshot), 1, 1) = '[' THEN TRIM(role_snapshot)
    ELSE '["' || REPLACE(REPLACE(TRIM(role_snapshot), ', ', ','), ',', '","') || '"]'
END
WHERE role_snapshot IS NOT NULL
  AND (TRIM(role_snapshot) = '' OR SUBSTRING(TRIM(role_snapshot), 1, 1) <> '[');
