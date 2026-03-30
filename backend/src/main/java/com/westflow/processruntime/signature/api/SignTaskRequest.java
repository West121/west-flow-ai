package com.westflow.processruntime.signature.api;

import jakarta.validation.constraints.NotBlank;

// 电子签章请求。
public record SignTaskRequest(
        // 签章类型。
        @NotBlank(message = "signatureType 不能为空")
        String signatureType,
        // 签章备注。
        String signatureComment
) {
}
