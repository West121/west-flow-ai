package com.westflow.plm.api;

import java.time.LocalDateTime;

/**
 * 项目阶段流转响应。
 */
public record PlmProjectStageEventResponse(
        String id,
        String fromPhaseCode,
        String toPhaseCode,
        String actionCode,
        String comment,
        String changedBy,
        String changedByDisplayName,
        LocalDateTime changedAt
) {
}
