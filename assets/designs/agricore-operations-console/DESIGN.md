# AgriCore Operations Console Design System

## Product Intent

Design a production-grade agricultural enterprise operations console named AgriCore. It manages farms, plots, crop cycles, field work, harvest, inventory, sales, IoT diagnostics, users, and safe public traceability. The interface is Vietnamese-first, operationally dense, calm, trustworthy, and audit-friendly.

Do not invent backend capabilities. Do not show decorative metrics, fake global search, unsupported list/history pages, or actions unavailable to the active role.

## Brand Character

- Grounded, precise, calm, modern, field-tested.
- Inspired by forest canopy, basalt soil, rice paper, and harvest gold without becoming rustic.
- Enterprise software, not a consumer gardening app.
- Avoid neon green, gradients, glassmorphism, oversized rounded cards, excessive pills, cartoon illustrations, and stock-photo hero panels inside authenticated screens.

## Color Tokens

- `brand-forest-900`: `#123B2A` — sidebar and strong brand surface.
- `brand-forest-700`: `#1F5D42` — primary action and selected navigation.
- `brand-forest-100`: `#DDECE3` — selected row and subtle highlight.
- `brand-harvest-600`: `#B67A16` — restrained accent and attention.
- `brand-harvest-100`: `#F7E9C8` — attention background.
- `brand-soil-700`: `#6E4E37` — land/origin metadata.
- `canvas`: `#F4F6F1`.
- `surface`: `#FFFFFF`.
- `text-primary`: `#17221B`.
- `text-secondary`: `#66736A`.
- `border`: `#D9DED8`.
- `success`: `#287A4B`.
- `warning`: `#A96712`.
- `danger`: `#B53B35`.
- `info`: `#2E6E83`.

Use semantic colors only for status. Pair color with icon and Vietnamese text. Target WCAG AA contrast.

Contrast guardrails:

- White on `brand-forest-700` is `7.77:1`; use it for primary actions.
- `text-secondary` on white is `4.97:1`; do not reduce its opacity.
- `brand-harvest-600` is an accent, border, or large-icon color only. It is `3.63:1` on white and must not carry normal-size text.
- On harvest/warning tint banners, use `text-primary` for copy. Use `warning` text only on white, not directly on the warm canvas.

## Typography

- Font family: Inter, fallback system sans-serif.
- Page title: 28px/36px, 700.
- Section title: 20px/28px, 650.
- Card title: 16px/24px, 600.
- Body: 14px/22px desktop; 16px/24px mobile forms.
- Labels and table headers: 12px/16px, 600.
- Codes, UUIDs, event IDs, SKU, farm/plot/cycle codes: ui-monospace, 12–13px.
- Use tabular numerals for quantities and KPIs.

## Spacing and Shape

- 8px spacing system: 4, 8, 12, 16, 24, 32, 40, 48.
- Card radius 12px; input/button radius 8px; badge radius 6px.
- Subtle one-pixel borders; shadows only for floating dialog/drawer.
- Desktop controls 40px high; touch controls at least 44px.

## Responsive Frame

- Desktop canvas 1440px. Fixed 264px forest sidebar, 72px white top bar, warm canvas content.
- Content padding 32px; max-width 1600px; 12-column grid.
- Tablet collapses sidebar to a 72px rail and stacks form/detail columns.
- Mobile public screen is 390px wide, single column, 16px padding, safe-area aware.
- Tables have sticky headers and horizontal overflow; mobile transforms table rows into labeled cards.

## Shared Navigation

Sidebar uses outline icons plus these stable labels:

- Tổng quan
- Nông trại
- Mùa vụ & công việc
- Thu hoạch
- Kho vận
- Bán hàng
- IoT
- Quản trị

Top bar shows breadcrumb, `Môi trường phát triển` badge, notification shortcut, and account menu. Only show search within a page that has a real query endpoint.

## Component Grammar

- One filled forest primary button per action region; secondary outline; tertiary text; destructive red.
- Persistent form labels, helper/error slot, optional marker expressed in text.
- Status badges are compact rounded rectangles with icon, Vietnamese label, optional enum below.
- Operational cards have clear title, metadata, action, and state. Do not make every section a card.
- Tables use 48px rows, sticky header, concise columns, row actions, page/size footer.
- Detail views use definition lists and copy buttons for IDs.
- Timeline and saga stepper show completed/current/future/failed with icon, label, timestamp, and line style.
- Inline banners remain visible for sync, conflict, permission, and terminal failure consequences.
- Drawers are used for contextual create/edit forms; dialogs only for confirmation.
- Skeleton loading mirrors final geometry. Empty and error states live inside the affected region.

## Data Formatting

- Vietnamese locale; dates `dd/MM/yyyy`, time `HH:mm`.
- Decimal comma and explicit units: `2,5 ha`, `840,0 kg`, `28,4 °C`, `62 %`, `pH 5,8`.
- Primary human label plus secondary exact code, for example `Nông trại Đắk Lắk` and `FARM-DL-01`.
- Keep backend status/code visible as secondary monospace text where support staff benefit.

## Sample Content

- User: `Nguyễn Minh Anh`, `FARM_MANAGER`, `minh.anh@agricore.vn`.
- Farm: `Nông trại An Phú`, code `FARM-AP01`, province `Đồng Tháp`.
- Plot: `Lô A2`, area `24,0 ha`; `Lô A3`, soil `BASALT`, area `31,2 ha`.
- Crop: `Lúa ST25` and `Cà phê Robusta`.
- Cycle: `Lúa ST25 · Hè Thu 2026`, stage `GROWING`, status `ACTIVE`.
- Task: `Kiểm tra mực nước ruộng`, type `INSPECTION`.
- Harvest: `HV-AP01-20260718-01`, grade `A`, net weight `124.200 kg`.
- Inventory item: `RICE-ST25-2026`, available quantity shown from on-hand minus reserved.

Use these as realistic design content only. Never imply unavailable aggregate/history APIs.

## Role Behavior

- SYSTEM_ADMIN: all core actions plus user role editing.
- FARM_MANAGER: farm, plot, cycle, task, harvest operations.
- AGRONOMIST: plot, cycle, task, harvest; no farm mutation.
- FIELD_WORKER: read core data, complete task, ingest IoT reading.
- WAREHOUSE_MANAGER: harvest and inventory operations.
- SALES_STAFF: customers/orders and reservation saga.
- AUDITOR: read-only views.

Hide irrelevant actions. When an unavailable action is important for discoverability, disable it and explain the required role.

## Critical State Language

- Projection: `Đang đồng bộ tồn kho và truy xuất` — no progress percentage and no success claim.
- Conflict: `Dữ liệu đã thay đổi. Tải lại trước khi thử lại.`
- Sales out of stock: `Không đủ tồn kho`; show terminal order body status even after HTTP 201.
- IoT cooldown: `Không tạo cảnh báo mới trong thời gian chờ`; preserve existing `OPEN` alert context.
- Public trace 404: `Chưa tìm thấy hoặc dữ liệu đang được đồng bộ`; offer retry and manual code entry.
- Notification sink: say `Đã ghi nhận thông báo`, not `Đã gửi thành công`.

## Accessibility

- Semantic landmarks and logical heading hierarchy.
- Visible 2px `#2E6E83` focus ring with 2px offset.
- Keyboard-accessible navigation, tabs, menus, drawers, dialogs, tables, and actions.
- Form errors tied to fields and summarized after submit.
- Icons have accessible names; status never relies on color alone.
- Touch target minimum 44×44px; reduced-motion safe transitions under 180ms.

## Screen Generation Rules

1. Use this same shell, palette, typography, spacing, and component grammar on every authenticated desktop screen.
2. Match the requested screen and exact verified actions; do not add unrelated widgets.
3. Prefer realistic operational density and alignment over visual novelty.
4. Include loading/empty/error/permission/sync states as compact component examples when requested, not full duplicate pages.
5. Public traceability has no authenticated shell and exposes no internal IDs, staff PII, costs, or operator controls.

## Export and Implementation Boundary

Stitch `screen.html` files are visual references, not production components. Reimplement them with semantic landmarks, native form associations, accessible names for icon actions, keyboard behavior, and application-owned CSS/font dependencies. Preserve the generated files unchanged for visual traceability; record production corrections in each screen's `screen.md`.
