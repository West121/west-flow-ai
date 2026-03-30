package com.westflow.system.monitor.model;

import java.time.Instant;

/**
 * 编排扫描结果快照记录。
 */
public record OrchestratorScanRecord(
        // 执行标识。
        String executionId,
        // 运行标识。
        String runId,
        // 目标标识。
        String targetId,
        // 目标名称。
        String targetName,
        // 自动化类型。
        String automationType,
        // 执行状态。
        String status,
        // 执行消息。
        String message,
        // 执行时间。
        Instant executedAt,
        // 扫描时间。
        Instant scannedAt
) {
}
