package com.westflow.plm.model;

/**
 * PLM 项目成员记录。
 */
public record PlmProjectMemberRecord(
        String id,
        String projectId,
        String userId,
        String roleCode,
        String roleLabel,
        String responsibilitySummary,
        int sortOrder
) {
}
