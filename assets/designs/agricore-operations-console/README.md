# AgriCore Operations Console — Stitch Design Package

## Overview

This package contains the Google Stitch source mapping and implementation-ready exports for the AgriCore responsive operations console and public traceability experience.

- Stitch project: `projects/14249723284313228807`
- Shared design system: `assets/13013554324954331763`
- Working branch: `feature/stitch-frontend-design`

## Design Scope

- Vietnamese-first authenticated desktop console.
- Tablet transformation guidance.
- Mobile-first public QR traceability result.
- Ten screens based on implemented controllers, DTOs, state machines, and RBAC.
- Explicit API-gap and asynchronous-state handling.

## Delivered Screens

| # | Screen | Target | Primary concern |
|---:|---|---|---|
| 01 | Đăng nhập | Desktop | Authentication and lock/error recovery |
| 02 | Tổng quan điều hành | Desktop | Shared shell and future aggregate data |
| 03 | Nông trại & lô canh tác | Desktop | Farm/plot master-detail |
| 04 | Chi tiết mùa vụ & công việc | Desktop | Stages, tasks, and version conflicts |
| 05 | Hoàn tất thu hoạch | Desktop | Harvest input and asynchronous projections |
| 06 | Chi tiết tồn kho & giữ hàng | Desktop | Item balance and reservation commands |
| 07 | Chi tiết đơn bán & saga | Desktop | Response-body saga outcomes |
| 08 | Chẩn đoán IoT | Desktop | Register/ingest immediate response flow |
| 09 | Quản trị người dùng & vai trò | Desktop | Paginated users and role updates |
| 10 | Truy xuất nguồn gốc công khai | Mobile | Safe public QR payload |

## Package Structure

```text
assets/designs/agricore-operations-console/
├── DESIGN.md
├── README.md
├── stitch-manifest.json
└── screens/
    ├── 01-login/
    ├── 02-dashboard/
    ├── 03-farms-plots/
    ├── 04-crop-cycle-work/
    ├── 05-harvest-completion/
    ├── 06-inventory-reservation/
    ├── 07-sales-saga/
    ├── 08-iot-diagnostics/
    ├── 09-user-role-admin/
    └── 10-public-traceability/
```

Each screen directory receives:

- `screen.png` — Stitch screenshot.
- `screen.html` — Stitch HTML export.
- `screen.md` — screen intent, verified actions/data, responsive and implementation notes.

## Source of Truth

1. Backend controller and DTO implementation.
2. `docs/design-guidelines.md` and this package's `DESIGN.md`.
3. `stitch-manifest.json` for remote project, design-system, and screen IDs.
4. OpenAPI only where it matches implementation.

## Current Backend Constraints

Do not implement populated list/history views for resources without query endpoints. Do not infer harvest projection completion, notification delivery, task ownership enforcement, or tenant isolation. See the CK scout report for the complete gap register.

| UI need | Current contract | Handoff decision |
|---|---|---|
| Dashboard KPIs/activity | No aggregate endpoint | Keep as labeled design data until a BFF/query contract exists. |
| Current-user task queue | No assignee/current-user filter | Do not silently fetch every task and filter as an authorization boundary. |
| Warehouse/item/reservation discovery | Create/get-by-ID and command endpoints only | Enter from a known resource ID; design list/search separately after API support. |
| Customer/order discovery | Create and order get-by-ID only | Show IDs honestly; do not invent customer names or order history. |
| IoT monitoring/history | Register device and ingest reading only | Use the request/response diagnostics screen; session log remains client-local. |
| Harvest → inventory/QR progress | Event-driven, no projection status | Show accepted/processing, then probe known detail/QR routes with retry. |
| Notification center | No notification list contract | Omitted from the ten-screen package. |
| Employee and crop-variety pickers | No suitable lookup contracts | Require lookup APIs before implementing production selectors. |

## Handoff

After design audit, use `ck:frontend-design` to implement these exports in the chosen frontend repository. Resolve API prerequisites before enabling future-only list/history features.

The generated `screen.html` files are visual source exports, not production-ready components. They currently load Tailwind and Google Fonts from CDNs and contain incomplete ARIA labeling. Rebuild them in the selected frontend stack, replace CDN dependencies with the application toolchain, preserve the Vietnamese `lang` metadata, and implement keyboard/focus/error behavior described in each `screen.md`.

Implementation order: shared tokens and shell → authentication → farm/crop/work → harvest/inventory/sales → IoT/admin → public traceability. Keep RBAC at both route and action level; UI hiding never replaces backend authorization.
