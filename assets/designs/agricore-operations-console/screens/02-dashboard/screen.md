# 02 — Tổng quan điều hành

- Stitch screen: `3155f73197024573ae55850dfb476e57`
- Device: desktop, `2560 × 3082` export
- Primary role: `FARM_MANAGER`

## Intent

Establish the authenticated shell and a role-oriented landing experience with priorities, crop-cycle progress, and recent activity.

## Contract anchors

- Farm and crop-cycle links route to implemented resource views.
- KPI values, current-user tasks, and recent activity are visual specimens only: the backend has no aggregate dashboard/BFF or current-user task query.
- Keep the in-product API-scope warning until an aggregate contract exists.

## Responsive behavior

At tablet width, collapse the sidebar to an icon rail or drawer, wrap KPI cards into two columns, then stack priorities above crop-cycle progress. Tables use horizontal containment rather than shrinking text below the design scale.

## Accessibility

Expose card labels before values, preserve table headers during horizontal scrolling, and pair every trend/status color with text and an icon.
