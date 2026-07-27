# 07 — Chi tiết đơn bán & saga

- Stitch screen: `b2d3608d1db64a9aba3fb4ab41d64b3f`
- Device: desktop, `2560 × 2912` export
- Primary role: `SALES_STAFF`

## Intent

Explain the current order and orchestration outcome without presenting server-managed saga steps as manual UI actions.

## Contract anchors

- Create an order with `POST /api/v1/sales/orders`; open a known order with `GET /api/v1/sales/orders/{orderId}`.
- Branch on response-body `status`, `sagaStatus`, `sagaStep`, and `failureReason` even when order creation returns HTTP 201.
- `COMPLETED / CONFIRMED` maps to the success timeline; `FAILED / COMPENSATED` maps to the recovery specimen.
- The response contains IDs, not customer/item names or step history. Do not invent lookups or timestamps.
- Do not expose edit, cancel, ship, or retry actions because the controller does not implement them.

## Required states

Confirmed, out of stock, compensated failure, generic failure reason, and order not found. Use the correlation ID for support/debugging without exposing it on public surfaces.

## Responsive behavior

Stack the current-status and failure-specimen cards below order details. Convert the horizontal saga tracker to an ordered vertical timeline on narrow widths.
