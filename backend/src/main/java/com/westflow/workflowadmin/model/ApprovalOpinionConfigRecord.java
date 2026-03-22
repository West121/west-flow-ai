package com.westflow.workflowadmin.model;

import java.time.Instant;

/**
 * 审批意见配置持久化记录。
 */
public record ApprovalOpinionConfigRecord(
        String configId,
        String configCode,
        String configName,
        boolean enabled,
        String quickOpinionsJson,
        String toolbarActionsJson,
        String buttonStrategiesJson,
        String remark,
        Instant createdAt,
        Instant updatedAt
) {
}
