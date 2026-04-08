package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * PLM 实施证据响应。
 */
public record PlmImplementationEvidenceResponse(
        String id,
        String businessType,
        String billId,
        String taskId,
        String evidenceType,
        String evidenceName,
        String evidenceRef,
        String evidenceSummary,
        String uploadedBy,
        LocalDateTime createdAt
) {
}
