# Traceability data flow

```mermaid
flowchart LR
    harvest["Harvest service"]
    event["HarvestCompleted.v1"]
    kafka[("Kafka")]
    projector["Traceability projector"]
    dedupe[("Processed event marker")]
    readModel[("Public batch read model")]
    qr["QR URL and image"]
    gateway["API Gateway public route"]
    consumer["Produce consumer"]
    dlt[("DLT")]

    harvest --> event --> kafka --> projector
    projector --> dedupe
    projector --> readModel --> qr
    readModel --> gateway --> consumer
    projector -->|"Invalid or exhausted record"| dlt
```

The public API reads only the Traceability database. It does not join Farm,
Work, Harvest, or Inventory databases at scan time. The projector stores only
contract-approved product, farm, plot, harvest, quality, care-summary, and batch
fields; internal identifiers and employee data are not returned.
