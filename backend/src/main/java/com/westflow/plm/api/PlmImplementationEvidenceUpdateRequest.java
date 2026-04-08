package com.westflow.plm.api;

/**
 * PLM 实施证据更新请求。
 */
public record PlmImplementationEvidenceUpdateRequest(
        String evidenceType,
        String evidenceName,
        String evidenceRef,
        String evidenceSummary
) {
}
