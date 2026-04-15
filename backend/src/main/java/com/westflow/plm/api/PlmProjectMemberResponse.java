package com.westflow.plm.api;

/**
 * 项目成员响应。
 */
public record PlmProjectMemberResponse(
        String id,
        String userId,
        String displayName,
        String roleCode,
        String roleLabel,
        String responsibilitySummary,
        int sortOrder
) {
}
