package com.westflow.system.file.api;

import java.time.Instant;

/**
 * 文件详情响应。
 */
public record SystemFileDetailResponse(
        String fileId,
        String displayName,
        String originalFilename,
        String bucketName,
        String objectName,
        String contentType,
        long fileSize,
        String remark,
        String status,
        String downloadUrl,
        String previewUrl,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt
) {
}
