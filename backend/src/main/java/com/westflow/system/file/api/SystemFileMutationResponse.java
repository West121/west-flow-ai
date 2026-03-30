package com.westflow.system.file.api;

/**
 * 文件上传与更新的返回结果。
 */
public record SystemFileMutationResponse(
        // 文件标识。
        String fileId
) {
}
