# 04 — Chi tiết mùa vụ & công việc

- Stitch screen: `f5dfe22781ab4d2a98f123df90c0bf02`
- Device: desktop, `2560 × 2048` export
- Primary roles: `FARM_MANAGER`, `AGRONOMIST`, `FIELD_WORKER`

## Intent

Combine crop-cycle stage context with daily work execution while keeping version conflicts and permission-sensitive actions visible.

## Contract anchors

- Stage transitions and crop-cycle updates follow the crop-cycle controller and versioned DTO.
- Task create/update/complete actions follow the work service.
- Do not expose Start, Cancel, or an automatic Overdue mutation: those endpoints do not exist.
- A non-admin employee picker and a current-user task filter require new lookup/query contracts.

## Required states

- Loading, no tasks, filtered empty, validation failure, forbidden action, and optimistic-lock conflict.
- Refresh the resource before retrying after a version conflict; do not overwrite silently.

## Responsive behavior

Keep the stage timeline horizontally scrollable on tablet. Move selected-task detail to a drawer and keep the conflict callout above sticky actions.
