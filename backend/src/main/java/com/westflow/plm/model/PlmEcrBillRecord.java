package com.westflow.plm.model;

/**
 * PLM ECR 变更申请记录。
 */
public record PlmEcrBillRecord(
        String id,
        String billNo,
        String sceneCode,
        String changeTitle,
        String changeReason,
        String affectedProductCode,
        String priorityLevel,
        String processInstanceId,
        String status,
        String creatorUserId
) {
}
