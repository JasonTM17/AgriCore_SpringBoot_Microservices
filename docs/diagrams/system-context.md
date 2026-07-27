# System context

```mermaid
flowchart LR
    operator["Farm operator"]
    consumer["Produce consumer"]
    device["Field sensor"]
    admin["Platform administrator"]

    agricore["AgriCore platform"]
    smtp["SMTP service"]
    provider["Optional assistant provider"]

    operator -->|"Operate farms, work, stock, harvest, and sales"| agricore
    admin -->|"Manage identities and deployment"| agricore
    consumer -->|"Scan public traceability QR"| agricore
    device -->|"Authenticated MQTT telemetry"| agricore
    agricore -->|"Delivery attempts"| smtp
    agricore -.->|"Bounded generation when configured"| provider
```

Trust boundaries:

- Browser and public QR traffic enter through the same-origin console edge and
  API gateway.
- Devices authenticate at the MQTT broker and can publish only to their own
  telemetry topic in the local profile.
- The assistant provider is disabled by default; its credential is a deployment
  secret and never a repository value.
