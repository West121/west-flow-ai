package com.westflow.system.role.response;

import java.util.List;

/**
 * 角色详情响应。
 */
public record SystemRoleDetailResponse(
        // 角色主键。
        String roleId,
        // 角色编码。
        String roleCode,
        // 角色名称。
        String roleName,
        // 角色分类。
        String roleCategory,
        // 角色说明。
        String description,
        // 已关联菜单主键列表。
        List<String> menuIds,
        // 数据权限范围配置。
        List<RoleDataScopeItem> dataScopes,
        // 是否启用。
        boolean enabled
) {

    public record RoleDataScopeItem(
            // 数据范围类型。
            String scopeType,
            // 数据范围值。
            String scopeValue
    ) {
    }
}
