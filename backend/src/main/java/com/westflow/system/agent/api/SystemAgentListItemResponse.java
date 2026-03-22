package com.westflow.system.agent.api;

import java.time.OffsetDateTime;

/**
 * 代理关系列表项响应。
 */
public record SystemAgentListItemResponse(
        String agentId,
        String principalUserId,
        String principalDisplayName,
        String principalUsername,
        String principalDepartmentName,
        String principalPostName,
        String delegateUserId,
        String delegateDisplayName,
        String delegateUsername,
        String delegateDepartmentName,
        String delegatePostName,
        String status,
        String remark,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
}
