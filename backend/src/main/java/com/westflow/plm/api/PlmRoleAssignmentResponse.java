package com.westflow.plm.api;

/**
 * PLM 角色矩阵响应。
 */
public record PlmRoleAssignmentResponse(
        String id,
        String businessType,
        String billId,
        String roleCode,
        String roleLabel,
        String assigneeUserId,
        String assigneeDisplayName,
        String assignmentScope,
        boolean required,
        String status,
        Integer sortOrder
) {
}
