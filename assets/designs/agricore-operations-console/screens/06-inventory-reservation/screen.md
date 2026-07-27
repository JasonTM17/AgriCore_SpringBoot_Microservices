# 06 — Chi tiết tồn kho & giữ hàng

- Stitch screen: `10c199c651d749388da45fa92a4fb386`
- Device: desktop, `3088 × 2640` export
- Primary roles: `WAREHOUSE_MANAGER`, `SALES_STAFF`

## Intent

Show the authoritative balance for one known inventory item and expose reservation creation, confirmation, and release without implying unsupported discovery APIs.

## Contract anchors

- Read a known item with `GET /api/v1/inventory/items/{itemId}`.
- Map stock-in, create reservation, release, and confirm to the implemented inventory controller.
- Render `onHandQuantity`, `reservedQuantity`, `availableQuantity`, and `version` from `InventoryItemResponse`.
- Open a reservation only when its ID is supplied by the calling flow; no reservation list exists.
- Do not fabricate warehouse/item search or stock-movement history.

## Required states

Insufficient available stock, reservation created, reservation confirmed, reservation released, forbidden action, and stale item balance. Reload item data before resubmitting a quantity after a conflict.

## Responsive behavior

Stack reservation cards below item details on tablet. Preserve the balance equation and keep destructive Release visually distinct from Confirm.
