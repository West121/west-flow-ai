package com.westflow.ai.service;

import com.westflow.ai.model.AiCopilotAssetResponse;
import com.westflow.common.error.ContractException;
import com.westflow.system.file.mapper.SystemFileMapper;
import com.westflow.system.file.model.SystemFileRecord;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * AI Copilot 附件服务。
 */
@Service
@RequiredArgsConstructor
public class AiCopilotAssetService {

    private static final String DEFAULT_BUCKET_NAME = "west-flow-ai";

    private final SystemFileMapper systemFileMapper;

    /**
     * 上传 Copilot 附件。
     */
    @Transactional
    public AiCopilotAssetResponse upload(MultipartFile file, String displayName) {
        if (file == null || file.isEmpty()) {
            throw new ContractException("VALIDATION.REQUEST_INVALID", HttpStatus.BAD_REQUEST, "请上传附件");
        }
        String fileId = buildId("aif");
        Instant now = Instant.now();
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String resolvedDisplayName = normalizeNullable(displayName);
        if (resolvedDisplayName == null) {
            resolvedDisplayName = originalFilename;
        }
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ContractException(
                    "SYS.FILE_READ_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "附件读取失败",
                    Map.of("fileId", fileId)
            );
        }
        String contentType = normalizeNullable(file.getContentType());
        systemFileMapper.upsert(
                new SystemFileRecord(
                        fileId,
                        resolvedDisplayName,
                        originalFilename,
                        DEFAULT_BUCKET_NAME,
                        buildObjectName(fileId, originalFilename),
                        contentType == null ? "application/octet-stream" : contentType,
                        file.getSize(),
                        "AI Copilot asset",
                        false,
                        now,
                        now,
                        null
                ),
                content
        );
        return toResponse(requireAsset(fileId));
    }

    /**
     * 查询附件元数据。
     */
    public AiCopilotAssetResponse detail(String fileId) {
        return toResponse(requireAsset(fileId));
    }

    /**
     * 下载附件内容。
     */
    public byte[] downloadContent(String fileId) {
        SystemFileRecord record = requireAsset(fileId);
        if (record.deleted()) {
            throw notFound(fileId, "附件不存在");
        }
        byte[] content = systemFileMapper.selectContent(fileId);
        if (content == null) {
            throw notFound(fileId, "附件内容不存在");
        }
        return content;
    }

    /**
     * 查询附件内容类型。
     */
    public String contentType(String fileId) {
        return requireAsset(fileId).contentType();
    }

    /**
     * 查询附件文件名。
     */
    public String filename(String fileId) {
        return requireAsset(fileId).originalFilename();
    }

    private AiCopilotAssetResponse toResponse(SystemFileRecord record) {
        return new AiCopilotAssetResponse(
                record.fileId(),
                record.displayName(),
                record.contentType(),
                "/api/v1/ai/copilot/assets/" + record.fileId() + "/preview"
        );
    }

    private SystemFileRecord requireAsset(String fileId) {
        SystemFileRecord record = systemFileMapper.selectById(fileId);
        if (record == null) {
            throw notFound(fileId, "附件不存在");
        }
        return record;
    }

    private ContractException notFound(String fileId, String message) {
        return new ContractException(
                "BIZ.RESOURCE_NOT_FOUND",
                HttpStatus.NOT_FOUND,
                message,
                Map.of("fileId", fileId)
        );
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String buildObjectName(String fileId, String originalFilename) {
        String extension = "";
        int index = originalFilename.lastIndexOf('.');
        if (index >= 0 && index < originalFilename.length() - 1) {
            extension = originalFilename.substring(index);
        }
        return "ai/copilot-assets/" + fileId + extension;
    }

    private String normalizeFilename(String value) {
        String normalized = normalizeNullable(value);
        return normalized == null ? "unknown-file" : normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
