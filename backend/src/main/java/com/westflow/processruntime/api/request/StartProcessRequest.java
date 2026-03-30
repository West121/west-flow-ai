package com.westflow.processruntime.api.request;

import jakarta.validation.constraints.NotBlank;
import java.util.Map;

// 发起流程实例时的请求载荷。
public record StartProcessRequest(
        // 流程键。
        @NotBlank(message = "processKey 不能为空")
        String processKey,
        // 业务主键。
        String businessKey,
        // 业务类型。
        String businessType,
        // 表单数据。
        Map<String, Object> formData
) {
}
