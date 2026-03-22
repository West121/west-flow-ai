package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;

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
        String changeType
) {
}
