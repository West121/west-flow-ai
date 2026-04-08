package com.westflow.plm.api;

/**
 * PLM 实施模板响应。
 */
public record PlmImplementationTemplateResponse(
        String id,
        String businessType,
        String sceneCode,
        String templateCode,
        String templateName,
        String taskType,
        String defaultTaskTitle,
        String defaultOwnerRoleCode,
        Integer requiredEvidenceCount,
        Boolean verificationRequired,
        Integer sortOrder,
        Boolean enabled
) {
}
