package com.westflow.plm.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/**
 * PLM 的物料主数据变更申请创建请求。
 */
public record CreatePLMMaterialChangeBillRequest(
        String sceneCode,
        @NotBlank(message = "物料编码不能为空")
        String materialCode,
        @NotBlank(message = "物料名称不能为空")
        String materialName,
        @NotBlank(message = "变更原因不能为空")
        String changeReason,
        String changeType,
        String specificationChange,
        String oldValue,
        String newValue,
        String uom,
        String affectedSystemsText,
        @Valid
        @NotEmpty(message = "受影响对象不能为空")
        List<PlmAffectedItemRequest> affectedItems
) {

    public CreatePLMMaterialChangeBillRequest(
            String sceneCode,
            String materialCode,
            String materialName,
            String changeReason,
            String changeType
    ) {
        this(sceneCode, materialCode, materialName, changeReason, changeType, null, null, null, null, null, null);
    }

    public CreatePLMMaterialChangeBillRequest {
        affectedItems = affectedItems == null ? null : List.copyOf(affectedItems);
    }
}
