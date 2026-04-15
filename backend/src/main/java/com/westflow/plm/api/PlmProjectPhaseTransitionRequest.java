package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * 项目阶段流转请求。
 */
public record PlmProjectPhaseTransitionRequest(
        @NotBlank(message = "toPhaseCode 不能为空")
        String toPhaseCode,
        @NotBlank(message = "actionCode 不能为空")
        String actionCode,
        String comment,
        String status,
        LocalDate actualEndDate
) {
}
