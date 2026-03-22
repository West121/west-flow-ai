package com.westflow.system.role.api;

import java.time.OffsetDateTime;

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
