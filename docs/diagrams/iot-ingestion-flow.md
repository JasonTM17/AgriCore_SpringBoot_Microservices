# IoT ingestion flow

```mermaid
flowchart LR
    simulator["Deterministic sensor simulator"]
    broker["Authenticated MQTT broker"]
    quota["Per-device token bucket + in-flight cap"]
    ingestion["IoT MQTT ingestion"]
    dedupe["Reading ID deduplication"]
    readings[("Sensor readings")]
    rules["Versioned threshold evaluation"]
    alerts[("Alert state + cooldown fingerprint")]
    outbox[("IoT outbox")]
    kafka[("Kafka")]
    notification["Notification consumer"]
    offline["Offline detector"]

    simulator -->|"QoS 1 device topic"| broker
    broker --> quota --> ingestion --> dedupe
    dedupe --> readings
    dedupe --> rules --> alerts
    alerts --> outbox --> kafka --> notification
    offline -->|"Checks last-seen with row version"| alerts
```

HTTP ingestion shares the same application path after authentication. Stable
`readingId` values make QoS 1 redelivery idempotent; reusing an ID with different
payload data is rejected. Alert fingerprints and cooldowns suppress repeated
notifications. Device buckets, in-flight work, and tracked bucket count are
bounded before shared queue submission.
