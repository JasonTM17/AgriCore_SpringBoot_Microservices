# AgriCore Frontend Design Guidelines

## Overview

AgriCore uses a calm, trustworthy agricultural operations interface. The product serves farm managers, agronomists, field workers, warehouse teams, sales staff, auditors, and administrators. It prioritizes operational clarity, traceable state, and safe actions over decorative dashboards.

Canonical machine-readable design specification: [`../assets/designs/agricore-operations-console/DESIGN.md`](../assets/designs/agricore-operations-console/DESIGN.md).

## Product Principles

1. Show operational truth. Never claim an asynchronous projection is complete without evidence.
2. Make status and next action obvious. Codes and timestamps remain visible for auditability.
3. Respect roles. Hide or disable unavailable mutations with a short reason.
4. Prefer dense, readable workspaces over oversized cards.
5. Preserve context. Farm, plot, crop cycle, and reference codes stay visible across workflows.
6. Degrade honestly. Missing list/history APIs become clear unavailable states, not simulated data.

## Visual Language

- Personality: grounded, precise, calm, modern, field-tested.
- Avoid: neon green, glossy gradients, glassmorphism, excessive pills, decorative charts, stock-farm hero imagery inside the console.
- Use warm neutral backgrounds and restrained forest green for navigation and primary actions.
- Harvest gold is an accent, not a second primary color.
- Soil brown is reserved for land/origin context; semantic state colors keep their conventional meaning.

## Core Tokens

| Token | Value | Usage |
|---|---:|---|
| Forest 900 | `#123B2A` | Sidebar, strong brand surface |
| Forest 700 | `#1F5D42` | Primary buttons, selected navigation |
| Forest 100 | `#DDECE3` | Selected row, subtle highlight |
| Harvest 600 | `#B67A16` | Accent, planned attention |
| Harvest 100 | `#F7E9C8` | Warning-neutral highlight |
| Soil 700 | `#6E4E37` | Land/origin metadata |
| Canvas | `#F4F6F1` | Application background |
| Surface | `#FFFFFF` | Cards, tables, forms |
| Ink | `#17221B` | Primary text |
| Muted | `#66736A` | Secondary text |
| Border | `#D9DED8` | Dividers, inputs |
| Success | `#287A4B` | Completed, healthy |
| Warning | `#A96712` | Attention, syncing |
| Danger | `#B53B35` | Destructive/error |
| Info | `#2E6E83` | Informational, external tools |

All text/background combinations must target WCAG AA contrast. Never rely on color alone; pair state colors with icon and label.

## Typography and Data

- Typeface: Inter with system sans-serif fallback.
- Page title: 28/36, weight 650–700.
- Section title: 20/28, weight 650.
- Card title: 16/24, weight 600.
- Body: 14/22 desktop; 16/24 mobile forms.
- Labels and table headers: 12/16, weight 600, modest letter spacing.
- Codes, IDs, event references: `ui-monospace`, 12–13px.
- Numeric quantities use tabular figures.
- Locale: Vietnamese; dates `dd/MM/yyyy`; time `HH:mm`; decimal comma; explicit units such as `kg`, `ha`, `°C`, `%`, `pH`.
- Preserve backend enum/code in secondary monospace text when it improves support and audit work.

## Layout

### Desktop ≥ 1280px

- 264px fixed sidebar; 64px top bar; content max-width 1600px.
- Page padding 28–32px; section gap 24px; card padding 20–24px.
- Twelve-column grid. Operational detail usually 8+4 columns.
- Tables remain primary for pageable operational data; cards summarize or group actions.

### Tablet 768–1279px

- Collapsible 72px icon rail; top bar preserves context and alerts.
- Two-column layouts collapse to one when forms require more than 520px.
- Tables use priority columns plus horizontal scroll; row actions remain reachable.

### Mobile ≤ 767px

- No desktop sidebar. Use top app bar and role-aware bottom navigation only for authenticated PWA concepts.
- Public traceability is single-column at 390px, 16px padding, 44px minimum touch targets.
- Tables transform into labeled record cards; no clipped identifiers.

## Shared Application Shell

Sidebar order:

1. Tổng quan
2. Vận hành: Trang trại & lô, Vụ canh tác, Công việc
3. Chuỗi cung ứng: Thu hoạch, Kho & giữ hàng, Bán hàng
4. Theo dõi: Cảm biến & cảnh báo, Truy xuất, Gửi thông báo
5. Quản trị: Người dùng & vai trò, Công cụ vận hành

Top bar contains breadcrumb, environment badge, global search affordance only when a real search API exists, notification shortcut, and account menu. Never expose a global search box that cannot return results.

## Component Rules

- Buttons: 40px desktop, 44px mobile; one primary per region. Destructive actions require confirmation.
- Inputs: persistent label, helper/error slot, 40–44px height. Never use placeholder as the only label.
- Status badge: compact rounded rectangle, not fully pill-shaped; icon + Vietnamese label + optional enum.
- Tables: sticky header, row hover, checkbox only when batch actions exist, pagination footer, empty/error states within table frame.
- Detail summary: use definition lists for IDs, dates, status, and ownership; use monospace copy affordance for UUID/event IDs.
- Timeline/stepper: completed/current/future/failed are distinguishable by icon, label, time, and line style.
- Toasts confirm transient success; persistent consequences use inline banners.
- Loading: skeleton matching final geometry. Avoid full-page spinners after shell loads.
- Permission: disabled control only when discovering the action is useful; otherwise hide it. Explain the required role in tooltip/help text.

## Operational State Patterns

- Async projection: amber inline banner, `Đang đồng bộ tồn kho và truy xuất`, retry guidance, no fake progress percentage.
- Optimistic lock conflict: preserve form values, show `Dữ liệu đã thay đổi`, offer `Tải lại dữ liệu` before retry.
- Sales saga failure: show terminal order body status even when HTTP status is 201; show compensation outcome separately.
- IoT cooldown: `Không tạo cảnh báo mới` can coexist with an existing `OPEN` alert; never label the reading normal solely from `alertRaised=false`.
- Traceability 404: `Chưa tìm thấy hoặc dữ liệu đang được đồng bộ`; allow retry and manual code entry.

## Accessibility

- Logical heading order and landmark regions.
- Visible 2px focus ring using `#2E6E83` with 2px offset.
- Keyboard-accessible menus, drawers, tabs, dialogs, tables, and timeline actions.
- Form errors linked with `aria-describedby`; summary at form top after submit.
- Icon-only controls always have accessible names.
- Minimum target 44×44px on touch surfaces.
- Respect reduced motion; transitions ≤180ms and never block work.

## Content Language

- Use direct Vietnamese verbs: `Tạo vụ canh tác`, `Giao việc`, `Hoàn tất thu hoạch`, `Giữ hàng`, `Xác nhận giữ hàng`.
- Avoid success claims stronger than backend evidence. Render notification status from `REQUESTED`, `DELIVERING`, `SENT`, or `FAILED`; use `Đang đồng bộ` after harvest.
- Error messages state what happened, the affected object, and the safe next step.
- Preserve agricultural terms consistently: `Trang trại`, `Lô canh tác`, `Vụ canh tác`, `Công việc`, `Thu hoạch`, `Tồn kho`, `Giữ hàng`, `Truy xuất nguồn gốc`.

## API-Dependent Future Views

Do not implement authoritative list/history views for harvest, inventory items,
reservations, customers, orders, IoT devices/readings/alerts, or projection
delivery until matching endpoints exist. The Notification service now has a
persisted, paged administrative in-app inbox plus a mark-read operation; it is
not a general user-scoped notification center.

## References

- `docs/architecture/SYSTEM_ARCHITECTURE.md`
- `docs/security/microservices-authz.md`
- `contracts/openapi/*.yaml`
