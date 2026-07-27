# 05 — Hoàn tất thu hoạch

- Stitch screen: `97e087ae901b4e148d6a657b858cc5f5`
- Device: desktop, `2560 × 3484` export
- Primary roles: `FARM_MANAGER`, `AGRONOMIST`

## Intent

Provide a high-confidence finalization flow that records a harvest, ends the crop cycle, and explains the asynchronous inventory and public-traceability projections.

## Contract anchors

The form maps directly to `CompleteHarvestRequest`: `code`, `cropCycleId`, `plotId`, `warehouseId`, `productCode`, gross/net weight, `qualityGrade`, notes, and the safe denormalized traceability fields.

- Validate positive weights and `netWeightKg <= grossWeightKg` before submit.
- The warehouse selector needs context or a future lookup API because no warehouse list endpoint exists.
- Inventory and QR creation occur after `HarvestCompleted.v1`; no projection-status or reconciliation endpoint exists.
- Do not claim success for downstream projections immediately after the harvest request succeeds.

## Required states

Submitting, harvest accepted/processing, input validation, version conflict, downstream detail available, and downstream detail still projecting. Public fields must exclude internal IDs, costs, and staff data.

## Responsive behavior

Stack summary and asynchronous-flow cards below the form on tablet. Keep final confirmation and the primary action adjacent and visible.
