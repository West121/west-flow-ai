package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

// 发起流程实例时的请求载荷。
public record StartProcessRequest(
        @NotBlank(message = "processKey 不能为空")
        String processKey,
        String businessKey,
        String businessType,
        Map<String, Object> formData
) {
}
