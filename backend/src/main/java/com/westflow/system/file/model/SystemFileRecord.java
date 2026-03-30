package com.westflow.system.file.model;

import java.time.Instant;

/**
 * 文件元数据记录，保留 MinIO 相关的 bucket 和 object 信息。
 */
public record SystemFileRecord(
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
        // 是否已删除。
        boolean deleted,
        // 创建时间。
        Instant createdAt,
        // 更新时间。
        Instant updatedAt,
        // 删除时间。
        Instant deletedAt
) {
    public SystemFileRecord withUpdatedMeta(String displayName, String remark, Instant updatedAt) {
        return new SystemFileRecord(
                fileId,
                displayName,
                originalFilename,
                bucketName,
                objectName,
                contentType,
                fileSize,
                remark,
                deleted,
                createdAt,
                updatedAt,
                deletedAt
        );
    }

    public SystemFileRecord withDeleted(Instant deletedAt, Instant updatedAt) {
        return new SystemFileRecord(
                fileId,
                displayName,
                originalFilename,
                bucketName,
                objectName,
                contentType,
                fileSize,
                remark,
                true,
                createdAt,
                updatedAt,
                deletedAt
        );
    }
}
