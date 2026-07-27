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

## Browser authentication origin

The Identity Deployment sets `AGRICORE_WEB_ALLOWED_ORIGINS` only from the
explicit `identity.webAllowedOrigins` value. This prevents its local
`localhost` default from being used in a cluster, where the Console reaches
login, refresh, and logout through the same-origin Ingress and Gateway.

The chart default is the exact origin for its default Ingress host:
`https://agricore.local`. When `ingress.enabled=true`, rendering fails unless
`identity.webAllowedOrigins` exactly equals `https://<ingress.host>`. The
single-origin guard intentionally rejects wildcards, extra origins, an HTTP
origin, or a different host; configure a dedicated chart release for every
browser origin.

For example, set both values together in a production values file that pins
every application image by digest:

```bash
helm upgrade --install agricore infrastructure/helm/agricore \
  -f /secure/path/agricore-production-values.yaml \
  --set ingress.enabled=true \
  --set ingress.host=console.example.com \
  --set identity.webAllowedOrigins=https://console.example.com \
  --set identity.refreshCookieSecure=true \
  --set gateway.trustedProxyAddressPattern='^10[.]0[.]0[.][0-9]+$'
```

Use a TLS-terminating Ingress for browser authentication. Do not configure a
wildcard or a second cross-origin Console as a convenience bypass.

## Refresh-cookie transport security

The Identity Deployment sets `AGRICORE_REFRESH_COOKIE_SECURE` from the explicit
boolean `identity.refreshCookieSecure` value. It defaults to `true`, and the
chart rejects `identity.refreshCookieSecure=false` whenever
`ingress.enabled=true`. This ensures the HTTP-only refresh cookie is sent only
over HTTPS for an Ingress-backed Console.

For an explicit local HTTP or other non-Ingress render, set
`ingress.enabled=false` and `identity.refreshCookieSecure=false`; use the
matching HTTP origin in `identity.webAllowedOrigins`, for example
`http://localhost:5173`. This exception is only for local development and
must not be carried into production values.

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

## Optional assistant provider and RAG

The assistant remains provider-free by default. To use DeepSeek V4 Flash, set
`assistant.provider=openai`, `assistant.providerModel=deepseek-v4-flash`,
`assistant.providerBaseUrl=https://api.deepseek.com`, and create the Secret
named by `assistant.providerSecretName` with the key selected by
`assistant.providerApiKeyKey`.

Set `assistant.ragEnabled=true` to retrieve only the curated, versioned
knowledge installed in the assistant database. Keep the bounded defaults unless
load evidence supports a change. The chart never accepts the provider key in
`values.yaml`.

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
