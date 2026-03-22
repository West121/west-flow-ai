package com.westflow.system.role.api;

import java.util.List;

/**
 * 角色详情响应。
 */
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
