# AgriCore Helm chart

## Immutable images

Production values must set every service `image` directly to
`repository@sha256:<64-hex-digest>`. `global.requireImageDigest=true` is the
default and fails closed for tag-based references, preventing a registry tag
from changing the deployed bytes after review. The chart validates and renders
the digest without appending `global.imageTag`. The `latest` tag is always
rejected.

CI may set `global.requireImageDigest=false` only while it validates a
full-40-character commit-SHA candidate tag. For a local development render,
also set `global.allowMutableImages=true` for a named non-`latest` tag. Never
carry either override into production values.

## Required internal Inventory credential

Create the Secret named by `inventory.internalCredentialSecretName` before
installing the chart. Its `inventory.internalCredentialTokenKey` entry must
contain a cryptographically random token of at least 32 characters. The chart
mounts the same value into Inventory, Work, Harvest, and Sales so background
operations can authenticate without storing an end-user JWT.

```bash
kubectl create secret generic agricore-inventory-internal \
  --from-literal=token='<random-token-from-your-secret-manager>'
```

Do not commit the token in a values file. Rotate it by updating the Secret and
rolling the four consuming Deployments.

## IoT TimescaleDB upgrades

The chart requires an operator-provided `agricore_iot` database with the
`timescaledb` extension already installed. The default pre-install/pre-upgrade
hook checks the extension using credentials from
`iot.timescalePreflight.credentialSecretName`. The hook only reads
`pg_extension`; it never creates or upgrades extensions.

IoT uses the `Recreate` deployment strategy because Flyway can migrate
`sensor_readings` into a hypertable. Schedule a maintenance window: existing
IoT pods stop before the new revision starts, ingestion is unavailable while
V4 moves existing data, and migration duration grows with reading volume.
Back up the IoT database and verify available disk space before upgrading.

`iot.timescalePreflight.unsafeSkip=true` is an explicit unsafe bypass. Use it
only when an equivalent operator-controlled check has already verified the
target database and extension. It does not change the IoT migration requirement.
