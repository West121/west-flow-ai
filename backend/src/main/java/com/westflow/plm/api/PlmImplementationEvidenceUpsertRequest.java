package com.westflow.plm.api;

/**
 * PLM 实施证据创建请求。
 */
public record PlmImplementationEvidenceUpsertRequest(
        String evidenceType,
        String evidenceName,
        String evidenceRef,
        String evidenceSummary
) {
}
