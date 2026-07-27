# 03 — Nông trại & lô canh tác

- Stitch screen: `20866bb339694355addb3fd765264994`
- Device: desktop, `2560 × 2048` export
- Primary roles: `SYSTEM_ADMIN`, `FARM_MANAGER`, `AGRONOMIST`

## Intent

Give operators a master-detail workspace for selecting a farm, reviewing its plots, and opening a plot without losing context.

## Contract anchors

- Farm and plot list/detail/create/update behavior must map to the farm service controllers and DTOs.
- The right panel displays the selected plot only; do not infer geospatial boundaries.
- The map empty state is intentional because the current backend does not expose plot geometry.

## Responsive behavior

At tablet width, render farm selection as a top selector, keep the plot table full width, and open plot details in a side sheet. On phone widths, use a list-detail navigation sequence.

## Accessibility

Maintain a programmatic selected farm/plot state, keyboard row activation, sortable-column announcements, and explicit status text alongside color.
