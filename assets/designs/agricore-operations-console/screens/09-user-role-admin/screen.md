# 09 — Quản trị người dùng & vai trò

- Stitch screen: `e74f246ac7254b7d92e9970f8bda7ca8`
- Device: desktop, `2560 × 2048` export
- Required role: `SYSTEM_ADMIN`

## Intent

Support paginated user review and least-privilege role changes while making unsupported account actions visibly unavailable.

## Contract anchors

- The table maps to `GET /api/v1/admin/users?page=&size=` and `UserResponse`.
- Role save maps to `PATCH /api/v1/admin/users/{userId}/roles` with a non-empty role set.
- Use only the exact role codes: `SYSTEM_ADMIN`, `FARM_MANAGER`, `AGRONOMIST`, `FIELD_WORKER`, `WAREHOUSE_MANAGER`, `SALES_STAFF`, `AUDITOR`.
- User creation, account locking, password reset, and status mutation are not provided by this controller.

## Required states

Loading page, empty page, locked/disabled user, no roles selected, role update success, forbidden, and concurrent refresh. Confirm high-impact role additions before submit.

## Responsive behavior

On tablet, keep the table primary and open role editing in a full-height side sheet. On phone, use a user list followed by a dedicated edit route.
