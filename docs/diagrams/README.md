# AgriCore architecture diagrams

These Mermaid v11 diagrams describe the deployed code paths. They are split by
concern so reviewers can compare each edge with the referenced contracts,
configuration, and services.

| Diagram | Purpose |
|---|---|
| [System context](system-context.md) | Users, external boundaries, and platform dependencies |
| [Container architecture](container-architecture.md) | Runtime containers and data ownership |
| [Service dependencies](service-dependencies.md) | Synchronous and asynchronous service edges |
| [Crop lifecycle sequence](crop-lifecycle-sequence.md) | Plot-to-harvest business sequence |
| [Harvest event flow](harvest-event-flow.md) | Transactional completion and projections |
| [Inventory reservation saga](inventory-reservation-saga.md) | Sales reservation, confirmation, and compensation |
| [Transactional outbox](transactional-outbox-flow.md) | Atomic write and bounded publication |
| [IoT ingestion](iot-ingestion-flow.md) | MQTT reading, alerting, and notification |
| [Traceability data flow](traceability-data-flow.md) | Public QR read-model boundary |
| [Deployment architecture](deployment-architecture.md) | Local Compose and operator-managed cluster scope |

Source of truth remains the implementation and versioned contracts. Update a
diagram in the same change whenever an edge, owner, or trust boundary changes.
