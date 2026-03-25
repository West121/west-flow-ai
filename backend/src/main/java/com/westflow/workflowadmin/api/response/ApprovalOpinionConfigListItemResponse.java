package com.westflow.workflowadmin.api.response;

import java.time.Instant;

/**
 * 审批意见配置列表项。
 */
public record ApprovalOpinionConfigListItemResponse(
        String configId,
        String configCode,
        String configName,
        String status,
        int quickOpinionCount,
        Instant updatedAt
) {
}
