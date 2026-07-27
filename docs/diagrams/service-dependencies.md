# Service dependencies

```mermaid
flowchart LR
    gateway["API Gateway"]
    identity["Identity + JWKS"]
    farm["Farm access authority"]
    catalog["Crop catalog"]
    cycle["Crop cycle"]
    work["Work"]
    harvest["Harvest"]
    inventory["Inventory"]
    traceability["Traceability"]
    iot["IoT"]
    sales["Sales"]
    notification["Notification"]
    assistant["Assistant"]
    kafka[("Kafka")]

    gateway --> identity
    gateway --> farm
    gateway --> catalog
    gateway --> cycle
    gateway --> work
    gateway --> harvest
    gateway --> inventory
    gateway --> traceability
    gateway --> iot
    gateway --> sales
    gateway --> notification
    gateway --> assistant

    cycle -->|"Bearer-forwarded farm/plot access check"| farm
    work -->|"Bearer-forwarded plot access check"| farm
    harvest -->|"Bearer-forwarded plot access check"| farm
    harvest -->|"Verify farm/plot crop-cycle scope"| cycle
    inventory -->|"Bearer-forwarded farm access check"| farm
    iot -->|"Bearer-forwarded plot access check"| farm
    sales -->|"Bearer-forwarded farm access check"| farm
    assistant -->|"Allowlisted read-only farm tools"| farm
    sales -->|"Farm-scoped reserve, lookup, confirm, release"| inventory

    identity -->|"UserRegistered.v1"| kafka
    farm --> kafka
    cycle --> kafka
    work --> kafka
    harvest --> kafka
    inventory --> kafka
    traceability --> kafka
    iot --> kafka
    sales --> kafka
    notification --> kafka

    kafka --> inventory
    kafka --> traceability
    kafka --> notification
```

All servlet applications independently validate RS256 access tokens against the
Identity JWKS. Kafka arrows show implemented producers and consumers; they do
not imply a shared transaction.
