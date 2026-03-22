package com.westflow.system.file.model;

import java.time.Instant;

/**
 * 文件元数据记录，保留 MinIO 相关的 bucket 和 object 信息。
 */
public record SystemFileRecord(
        String fileId,
        String displayName,
        String originalFilename,
        String bucketName,
        String objectName,
        String contentType,
        long fileSize,
        String remark,
        boolean deleted,
        Instant createdAt,
        Instant updatedAt,
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
