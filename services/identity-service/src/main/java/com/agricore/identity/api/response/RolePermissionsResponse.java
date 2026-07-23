package com.agricore.identity.api.response;

import java.util.List;

public record RolePermissionsResponse(String role, long version, List<PermissionResponse> permissions) {
}
