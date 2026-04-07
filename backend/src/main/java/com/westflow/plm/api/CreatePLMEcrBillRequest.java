package com.westflow.plm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * PLM 的 ECR 变更申请创建请求。
 */
public record CreatePLMEcrBillRequest(
        String sceneCode,
        @NotBlank(message = "变更标题不能为空")
        String changeTitle,
        @NotBlank(message = "变更原因不能为空")
        String changeReason,
        String affectedProductCode,
        String priorityLevel,
        String changeCategory,
        String targetVersion,
        String affectedObjectsText,
        String impactScope,
        String riskLevel,
        @Valid
        @NotEmpty(message = "受影响对象不能为空")
        List<PlmAffectedItemRequest> affectedItems
) {

    public CreatePLMEcrBillRequest(
            String sceneCode,
            String changeTitle,
            String changeReason,
            String affectedProductCode,
            String priorityLevel
    ) {
        this(sceneCode, changeTitle, changeReason, affectedProductCode, priorityLevel, null, null, null, null, null, null);
    }

    public CreatePLMEcrBillRequest {
        affectedItems = affectedItems == null ? null : List.copyOf(affectedItems);
    }
}
