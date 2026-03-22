package com.westflow.oa.api;

import jakarta.validation.constraints.NotBlank;

/**
 * OA 通用申请单的创建请求载体。
 */
public record CreateOACommonRequestBillRequest(
        String sceneCode,
        @NotBlank(message = "申请标题不能为空")
        String title,
        @NotBlank(message = "申请内容不能为空")
        String content
) {
}
