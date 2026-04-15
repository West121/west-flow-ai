package com.westflow.plm.model;

import java.time.LocalDateTime;

/**
 * PLM 项目阶段流转事件。
 */
public record PlmProjectStageEventRecord(
        String id,
        String projectId,
        String fromPhaseCode,
        String toPhaseCode,
        String actionCode,
        String comment,
        String changedBy,
        LocalDateTime changedAt
) {
}
