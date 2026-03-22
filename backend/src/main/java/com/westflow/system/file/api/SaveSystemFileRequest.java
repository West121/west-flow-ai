package com.westflow.system.file.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 文件元数据更新请求。
 */
public record SaveSystemFileRequest(
        @NotBlank(message = "文件显示名不能为空")
        String displayName,
        String remark
) {
}
