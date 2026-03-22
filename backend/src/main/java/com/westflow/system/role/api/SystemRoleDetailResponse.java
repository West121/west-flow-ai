package com.westflow.system.role.api;

import java.util.List;

public record SystemRoleDetailResponse(
        String roleId,
        String roleCode,
        String roleName,
        String roleCategory,
        String description,
        List<String> menuIds,
        List<RoleDataScopeItem> dataScopes,
        boolean enabled
) {

    public record RoleDataScopeItem(
            String scopeType,
            String scopeValue
    ) {
    }
}
