package com.westflow.system.file.api;

import java.time.Instant;

/**
 * 文件列表项响应。
 */
public record SystemFileListItemResponse(
        String fileId,
        String displayName,
        String originalFilename,
        String bucketName,
        String objectName,
        String contentType,
        long fileSize,
        String status,
        Instant createdAt
) {
}
