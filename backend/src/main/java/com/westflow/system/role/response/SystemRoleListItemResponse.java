package com.westflow.system.role.response;

import java.time.OffsetDateTime;

/**
 * 角色列表项响应。
 */
public record SystemRoleListItemResponse(
        // 角色主键。
        String roleId,
        // 角色编码。
        String roleCode,
        // 角色名称。
        String roleName,
        // 角色分类。
        String roleCategory,
        // 数据权限摘要。
        String dataScopeSummary,
        // 菜单数量。
        Integer menuCount,
        // 启用状态。
        String status,
        // 创建时间。
        OffsetDateTime createdAt
) {
}
