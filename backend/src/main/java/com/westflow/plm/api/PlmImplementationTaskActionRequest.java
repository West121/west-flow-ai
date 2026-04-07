package com.westflow.plm.api;

/**
 * PLM 实施任务动作请求。
 */
public record PlmImplementationTaskActionRequest(
        String ownerUserId,
        String resultSummary
) {
}
