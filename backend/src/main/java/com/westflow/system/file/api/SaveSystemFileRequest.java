package com.westflow.system.file.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 文件元数据更新请求。
 */
public record SaveSystemFileRequest(
        // 展示名称。
        @NotBlank(message = "文件显示名不能为空")
        String displayName,
        // 备注。
        String remark
) {
}
