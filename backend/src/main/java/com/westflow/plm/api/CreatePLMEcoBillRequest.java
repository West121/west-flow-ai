package com.westflow.plm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.time.LocalDate;
import java.util.List;

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
        String changeReason,
        String implementationOwner,
        String targetVersion,
        String rolloutScope,
        String validationPlan,
        String rollbackPlan,
        @Valid
        @NotEmpty(message = "受影响对象不能为空")
        List<PlmAffectedItemRequest> affectedItems
) {

    public CreatePLMEcoBillRequest(
            String sceneCode,
            String executionTitle,
            String executionPlan,
            LocalDate effectiveDate,
            String changeReason
    ) {
        this(sceneCode, executionTitle, executionPlan, effectiveDate, changeReason, null, null, null, null, null, null);
    }

    public CreatePLMEcoBillRequest {
        affectedItems = affectedItems == null ? null : List.copyOf(affectedItems);
    }
}
