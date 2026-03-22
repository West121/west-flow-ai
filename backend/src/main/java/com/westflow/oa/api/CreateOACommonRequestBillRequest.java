package com.westflow.oa.api;

import jakarta.validation.constraints.NotBlank;

public record CreateOACommonRequestBillRequest(
        String sceneCode,
        @NotBlank(message = "申请标题不能为空")
        String title,
        @NotBlank(message = "申请内容不能为空")
        String content
) {
}
