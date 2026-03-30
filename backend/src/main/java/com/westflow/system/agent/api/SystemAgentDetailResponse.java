package com.westflow.system.agent.api;

import java.time.OffsetDateTime;

/**
 * 代理关系详情响应。
 */
public record SystemAgentDetailResponse(
        // 代理关系标识。
        String agentId,
        // 主体用户标识。
        String principalUserId,
        // 主体用户姓名。
        String principalDisplayName,
        // 主体用户名。
        String principalUsername,
        // 主体部门名称。
        String principalDepartmentName,
        // 主体岗位名称。
        String principalPostName,
        // 委派用户标识。
        String delegateUserId,
        // 委派用户姓名。
        String delegateDisplayName,
        // 委派用户名。
        String delegateUsername,
        // 委派部门名称。
        String delegateDepartmentName,
        // 委派岗位名称。
        String delegatePostName,
        // 记录状态。
        String status,
        // 备注说明。
        String remark,
        // 创建时间。
        OffsetDateTime createdAt,
        // 更新时间。
        OffsetDateTime updatedAt
) {
}
