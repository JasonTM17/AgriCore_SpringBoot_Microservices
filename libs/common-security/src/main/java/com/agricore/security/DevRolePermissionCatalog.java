package com.agricore.security;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Mirrors the migration-owned Identity grants for local development headers.
 *
 * <p>This catalog is never consulted for signed JWTs. It only keeps existing
 * dev-mode requests representative after domain endpoints enforce permissions.
 */
final class DevRolePermissionCatalog {

    private static final Set<String> ALL = Set.of(
            "IDENTITY_USER_READ", "IDENTITY_USER_ADMIN",
            "IDENTITY_POLICY_READ", "IDENTITY_POLICY_ADMIN",
            "FARM_READ", "FARM_WRITE", "FARM_ADMIN",
            "CROP_CATALOG_READ", "CROP_CATALOG_WRITE",
            "CROP_CYCLE_READ", "CROP_CYCLE_WRITE", "CROP_CYCLE_USE",
            "WORK_READ", "WORK_WRITE", "WORK_USE",
            "HARVEST_READ", "HARVEST_WRITE",
            "INVENTORY_READ", "INVENTORY_WRITE", "INVENTORY_USE",
            "SALES_READ", "SALES_WRITE", "SALES_USE",
            "IOT_READ", "IOT_WRITE", "IOT_USE",
            "TRACEABILITY_READ", "TRACEABILITY_WRITE", "TRACEABILITY_USE",
            "NOTIFICATION_READ", "NOTIFICATION_ADMIN",
            "ASSISTANT_USE"
    );

    private static final Set<String> READ_ONLY = Set.of(
            "IDENTITY_USER_READ", "IDENTITY_POLICY_READ", "FARM_READ",
            "CROP_CATALOG_READ", "CROP_CYCLE_READ", "WORK_READ",
            "HARVEST_READ", "INVENTORY_READ", "SALES_READ", "IOT_READ",
            "TRACEABILITY_READ", "NOTIFICATION_READ"
    );

    private static final Map<String, Set<String>> BY_ROLE = Map.of(
            "SYSTEM_ADMIN", ALL,
            "FARM_MANAGER", Set.of(
                    "FARM_READ", "FARM_WRITE", "FARM_ADMIN", "CROP_CATALOG_READ",
                    "CROP_CYCLE_READ", "CROP_CYCLE_WRITE", "CROP_CYCLE_USE",
                    "WORK_READ", "WORK_WRITE", "WORK_USE", "HARVEST_READ",
                    "HARVEST_WRITE", "INVENTORY_READ", "SALES_READ", "IOT_READ",
                    "IOT_WRITE", "TRACEABILITY_READ", "TRACEABILITY_USE", "ASSISTANT_USE"
            ),
            "AGRONOMIST", Set.of(
                    "FARM_READ", "FARM_WRITE", "CROP_CATALOG_READ", "CROP_CATALOG_WRITE",
                    "CROP_CYCLE_READ", "CROP_CYCLE_WRITE", "CROP_CYCLE_USE",
                    "WORK_READ", "WORK_WRITE", "WORK_USE", "HARVEST_READ",
                    "HARVEST_WRITE", "INVENTORY_READ", "SALES_READ", "IOT_READ",
                    "IOT_WRITE", "TRACEABILITY_READ", "TRACEABILITY_USE", "ASSISTANT_USE"
            ),
            "FIELD_WORKER", Set.of(
                    "FARM_READ", "CROP_CATALOG_READ", "CROP_CYCLE_READ",
                    "CROP_CYCLE_USE", "WORK_READ", "WORK_USE", "HARVEST_READ",
                    "INVENTORY_READ", "SALES_READ", "IOT_READ", "IOT_USE",
                    "TRACEABILITY_READ", "ASSISTANT_USE"
            ),
            "WAREHOUSE_MANAGER", Set.of(
                    "FARM_READ", "CROP_CATALOG_READ", "CROP_CYCLE_READ", "WORK_READ",
                    "HARVEST_READ", "HARVEST_WRITE", "INVENTORY_READ", "INVENTORY_WRITE",
                    "INVENTORY_USE", "SALES_READ", "SALES_USE", "IOT_READ",
                    "TRACEABILITY_READ", "TRACEABILITY_WRITE", "TRACEABILITY_USE",
                    "ASSISTANT_USE"
            ),
            "SALES_STAFF", Set.of(
                    "FARM_READ", "CROP_CATALOG_READ", "CROP_CYCLE_READ", "WORK_READ",
                    "HARVEST_READ", "INVENTORY_READ", "INVENTORY_USE", "SALES_READ",
                    "SALES_WRITE", "SALES_USE", "IOT_READ", "TRACEABILITY_READ",
                    "ASSISTANT_USE"
            ),
            "AUDITOR", READ_ONLY
    );

    private DevRolePermissionCatalog() {
    }

    static List<String> resolve(Collection<String> roles) {
        Set<String> permissions = new TreeSet<>();
        roles.forEach(role -> permissions.addAll(BY_ROLE.getOrDefault(role, Set.of())));
        return List.copyOf(permissions);
    }
}
