package com.agricore.identity.api.response;

import java.util.List;

public record RolePermissionsResponse(String role, List<PermissionResponse> permissions) {
}
