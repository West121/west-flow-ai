package com.westflow.system.file.api;

import java.time.Instant;

/**
 * 文件详情响应。
 */
public record SystemFileDetailResponse(
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
        // 备注。
        String remark,
        // 文件状态。
        String status,
        // 下载地址。
        String downloadUrl,
        // 预览地址。
        String previewUrl,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt,
        // 删除时间。
        Instant deletedAt
) {
}
