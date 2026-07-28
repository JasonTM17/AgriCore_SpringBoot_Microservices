# Transactional outbox flow

```mermaid
sequenceDiagram
    autonumber
    participant API
    participant Service
    participant DB
    participant Poller
    participant Kafka
    participant Consumer
    participant ConsumerDB

    API->>Service: Mutating command
    Service->>DB: Begin transaction
    Service->>DB: Write aggregate
    Service->>DB: Write versioned event envelope to outbox
    Service->>DB: Commit
    Service-->>API: Success

    loop bounded polling batch
        Poller->>DB: Lock unpublished rows with skip locked
        Poller->>Kafka: Send with bounded timeout
        alt broker acknowledges
            Poller->>DB: Mark published
        else send fails
            Poller->>DB: Increment attempt and retain error
        end
    end

    Kafka-->>Consumer: At-least-once delivery
    Consumer->>ConsumerDB: Persist processed event and side effect atomically
```

Pollers expose backlog metrics and keep failed rows repairable. Sales and
Notification retain quarantined rows for the bounded
`OUTBOX_QUARANTINE_RETENTION` recovery window (default `P7D`); operators must
review or repair them before cleanup. Other services retain unpublished evidence
until their recovery path resolves it.
