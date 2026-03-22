package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/**
 * PLM 的 ECO 变更执行创建请求。
 */
public record CreatePLMEcoBillRequest(
        String sceneCode,
        @NotBlank(message = "执行标题不能为空")
        String executionTitle,
        @NotBlank(message = "执行计划不能为空")
        String executionPlan,
        LocalDate effectiveDate,
        @NotBlank(message = "变更原因不能为空")
        String changeReason
) {
}
