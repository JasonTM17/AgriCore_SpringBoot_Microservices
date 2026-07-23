# Harvest event flow

```mermaid
flowchart LR
    request["Complete harvest request"]
    transaction["Harvest DB transaction"]
    harvest[("Harvest batch")]
    outbox[("Harvest outbox")]
    publisher["Locking outbox publisher"]
    kafka[("Kafka topic")]
    inventory["Inventory consumer"]
    trace["Traceability consumer"]
    inventoryDb[("Inventory ledger + processed event")]
    traceDb[("Traceability read model + processed event")]
    dlt[("Harvest topic DLT")]

    request --> transaction
    transaction --> harvest
    transaction --> outbox
    outbox --> publisher --> kafka
    kafka --> inventory --> inventoryDb
    kafka --> trace --> traceDb
    inventory -->|"Invalid envelope or exhausted retry"| dlt
    trace -->|"Invalid envelope or exhausted retry"| dlt
```

The harvest aggregate and outbox row commit atomically. Consumer transactions
make duplicate delivery safe; wrong event types, wrong versions, and malformed
envelopes follow the bounded retry/DLT policy documented in the Kafka runbook.
