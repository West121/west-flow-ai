package com.westflow.system.file.api;

import java.time.Instant;

/**
 * 文件列表项响应。
 */
public record SystemFileListItemResponse(
        // 文件主键。
        String fileId,
        // 展示名称。
        String displayName,
        // 原始文件名。
        String originalFilename,
        // 存储桶名称。
        String bucketName,
        // 对象存储键。
        String objectName,
        // 内容类型。
        String contentType,
        // 文件大小，单位字节。
        long fileSize,
        // 文件状态。
        String status,
        // 创建时间。
        Instant createdAt
) {
}
