package com.westflow.plm.api;

/**
 * PLM 验收清单更新请求。
 */
public record PlmAcceptanceChecklistUpdateRequest(
        String status,
        String resultSummary
) {
}
