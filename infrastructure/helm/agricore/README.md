# AgriCore Helm chart

## IoT TimescaleDB upgrades

The chart requires an operator-provided `agricore_iot` database with the
`timescaledb` extension already installed. The default pre-install/pre-upgrade
hook checks the extension using credentials from
`iot.timescalePreflight.credentialSecretName`, or
`postgres.databaseSecretName` when the IoT-specific name is empty. The hook
only reads `pg_extension`; it never creates or upgrades extensions.

IoT uses the `Recreate` deployment strategy because Flyway can migrate
`sensor_readings` into a hypertable. Schedule a maintenance window: existing
IoT pods stop before the new revision starts, ingestion is unavailable while
V4 moves existing data, and migration duration grows with reading volume.
Back up the IoT database and verify available disk space before upgrading.

`iot.timescalePreflight.unsafeSkip=true` is an explicit unsafe bypass. Use it
only when an equivalent operator-controlled check has already verified the
target database and extension. It does not change the IoT migration requirement.
