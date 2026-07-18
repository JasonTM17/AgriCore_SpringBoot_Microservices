# 10 — Truy xuất nguồn gốc công khai

- Stitch screen: `06a365df2af9461091e0a1c9f9a56a1c`
- Device: mobile, `780 × 3596` export representing a 390px CSS viewport
- Audience: unauthenticated public users

## Intent

Turn a scanned traceability code into a legible, privacy-safe product story without making unsupported certification or supply-chain claims.

## Contract anchors

Map only `PublicTraceabilityResponse`: `traceabilityCode`, `productName`, `varietyName`, `farmName`, `plotCode`, planting/harvest dates, `qualityGrade`, `netWeightKg`, `careSummary`, `qrUrl`, and `batchLabel`.

- Never expose internal UUIDs, staff names, contact data, costs, reservation/order data, or service diagnostics.
- Do not claim organic, pesticide-free, laboratory-tested, blockchain-backed, or certified unless a future verified field explicitly supplies it.
- A 404 can mean an invalid code or an asynchronous projection not yet available; present a neutral retry path without claiming which cause occurred.

## Required states

Success with all fields, nullable optional fields, code not found/still processing, invalid code format, network failure, retrying, share success, and copy success.

## Accessibility and responsive behavior

Use one-column mobile reading order, 44px controls, selectable/copyable code text, announced copy/share feedback, and semantic definition lists. The same layout may expand to a centered 520px column on larger screens.
