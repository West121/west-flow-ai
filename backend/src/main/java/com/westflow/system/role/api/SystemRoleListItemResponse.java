package com.westflow.system.role.api;

import java.time.OffsetDateTime;

/**
 * 角色列表项响应。
 */
public record SystemRoleListItemResponse(
        String roleId,
        String roleCode,
        String roleName,
        String roleCategory,
        String dataScopeSummary,
        Integer menuCount,
        String status,
        OffsetDateTime createdAt
) {
}
