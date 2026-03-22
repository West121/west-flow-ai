package com.westflow.system.file.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.system.file.api.SaveSystemFileRequest;
import com.westflow.system.file.api.SystemFileDetailResponse;
import com.westflow.system.file.api.SystemFileListItemResponse;
import com.westflow.system.file.api.SystemFileMutationResponse;
import com.westflow.system.file.mapper.SystemFileMapper;
import com.westflow.system.file.model.SystemFileRecord;
import java.io.IOException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 文件管理服务，基于 MinIO 元数据结构管理上传、下载和逻辑删除。
 */
@Service
@RequiredArgsConstructor
public class SystemFileService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "updatedAt", "displayName", "fileSize");
    private static final String DEFAULT_BUCKET_NAME = "west-flow-ai";

    private final SystemFileMapper systemFileMapper;
    private final IdentityAuthService fixtureAuthService;

    public PageResponse<SystemFileListItemResponse> page(PageRequest request) {
        ensureProcessAdmin();
        Filters filters = resolveFilters(request.filters());
        Comparator<SystemFileRecord> comparator = resolveComparator(request.sorts());
        List<SystemFileListItemResponse> records = systemFileMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.deleted() == null || filters.deleted().equals(record.deleted()))
                .sorted(comparator)
                .map(this::toListItem)
                .toList();

        long total = records.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(records.size(), fromIndex + request.pageSize());
        List<SystemFileListItemResponse> pageRecords = fromIndex >= records.size() ? List.of() : records.subList(fromIndex, toIndex);

        return new PageResponse<>(request.page(), pageSize, total, pages, pageRecords, List.of());
    }

    public SystemFileDetailResponse detail(String fileId) {
        ensureProcessAdmin();
        return toDetail(requireFile(fileId));
    }

    @Transactional
    public SystemFileMutationResponse upload(MultipartFile file, String displayName, String remark) {
        ensureProcessAdmin();
        if (file == null || file.isEmpty()) {
            throw new ContractException("VALIDATION.REQUEST_INVALID", HttpStatus.BAD_REQUEST, "请上传文件");
        }
        String fileId = buildId("fil");
        Instant now = Instant.now();
        String originalFilename = normalizeFilename(file.getOriginalFilename());
        String resolvedDisplayName = normalizeNullable(displayName);
        if (resolvedDisplayName == null) {
            resolvedDisplayName = originalFilename;
        }
        String objectName = buildObjectName(fileId, originalFilename);
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new ContractException(
                    "SYS.FILE_READ_FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "文件读取失败",
                    Map.of("fileId", fileId)
            );
        }

        systemFileMapper.upsert(
                new SystemFileRecord(
                        fileId,
                        resolvedDisplayName,
                        originalFilename,
                        DEFAULT_BUCKET_NAME,
                        objectName,
                        normalizeNullable(file.getContentType()) == null ? "application/octet-stream" : file.getContentType(),
                        file.getSize(),
                        normalizeNullable(remark),
                        false,
                        now,
                        now,
                        null
                ),
                content
        );
        return new SystemFileMutationResponse(fileId);
    }

    @Transactional
    public SystemFileMutationResponse update(String fileId, SaveSystemFileRequest request) {
        ensureProcessAdmin();
        SystemFileRecord existing = requireFile(fileId);
        if (existing.deleted()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "已删除文件不能修改",
                    Map.of("fileId", fileId)
            );
        }
        Instant now = Instant.now();
        systemFileMapper.update(existing.withUpdatedMeta(request.displayName().trim(), normalizeNullable(request.remark()), now));
        return new SystemFileMutationResponse(fileId);
    }

    @Transactional
    public SystemFileMutationResponse delete(String fileId) {
        ensureProcessAdmin();
        SystemFileRecord existing = requireFile(fileId);
        if (existing.deleted()) {
            return new SystemFileMutationResponse(fileId);
        }
        Instant now = Instant.now();
        systemFileMapper.update(existing.withDeleted(now, now));
        return new SystemFileMutationResponse(fileId);
    }

    public byte[] downloadContent(String fileId) {
        ensureProcessAdmin();
        SystemFileRecord record = requireFile(fileId);
        if (record.deleted()) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "文件不存在",
                    Map.of("fileId", fileId)
            );
        }
        byte[] content = systemFileMapper.selectContent(fileId);
        if (content == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "文件内容不存在",
                    Map.of("fileId", fileId)
            );
        }
        return content;
    }

    public String downloadFilename(String fileId) {
        return requireFile(fileId).originalFilename();
    }

    private SystemFileListItemResponse toListItem(SystemFileRecord record) {
        return new SystemFileListItemResponse(
                record.fileId(),
                record.displayName(),
                record.originalFilename(),
                record.bucketName(),
                record.objectName(),
                record.contentType(),
                record.fileSize(),
                record.deleted() ? "DELETED" : "ACTIVE",
                record.createdAt()
        );
    }

    private SystemFileDetailResponse toDetail(SystemFileRecord record) {
        return new SystemFileDetailResponse(
                record.fileId(),
                record.displayName(),
                record.originalFilename(),
                record.bucketName(),
                record.objectName(),
                record.contentType(),
                record.fileSize(),
                record.remark(),
                record.deleted() ? "DELETED" : "ACTIVE",
                buildDownloadUrl(record.fileId()),
                buildPreviewUrl(record.fileId()),
                record.createdAt(),
                record.updatedAt(),
                record.deletedAt()
        );
    }

    private boolean matchesKeyword(SystemFileRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return record.displayName().toLowerCase().contains(normalized)
                || record.originalFilename().toLowerCase().contains(normalized)
                || record.bucketName().toLowerCase().contains(normalized)
                || record.objectName().toLowerCase().contains(normalized)
                || (record.remark() != null && record.remark().toLowerCase().contains(normalized));
    }

    private Comparator<SystemFileRecord> resolveComparator(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return Comparator.comparing(SystemFileRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed();
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        Comparator<SystemFileRecord> comparator = switch (sort.field()) {
            case "displayName" -> Comparator.comparing(SystemFileRecord::displayName, Comparator.nullsLast(Comparator.naturalOrder()));
            case "fileSize" -> Comparator.comparingLong(SystemFileRecord::fileSize);
            case "updatedAt" -> Comparator.comparing(SystemFileRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
            default -> Comparator.comparing(SystemFileRecord::createdAt, Comparator.nullsLast(Comparator.naturalOrder()));
        };
        return "asc".equalsIgnoreCase(sort.direction()) ? comparator : comparator.reversed();
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean deleted = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            deleted = switch (value == null ? "" : value.toUpperCase()) {
                case "ACTIVE" -> false;
                case "DELETED" -> true;
                default -> throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "状态筛选值不合法",
                        Map.of("status", value, "allowedStatuses", List.of("ACTIVE", "DELETED"))
                );
            };
        }
        return new Filters(deleted);
    }

    private SystemFileRecord requireFile(String fileId) {
        SystemFileRecord record = systemFileMapper.selectById(fileId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "文件不存在",
                    Map.of("fileId", fileId)
            );
        }
        return record;
    }

    private void ensureProcessAdmin() {
        String userId = currentUserId();
        if (!fixtureAuthService.isProcessAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅系统管理员可以访问文件管理",
                    Map.of("userId", userId)
            );
        }
    }

    private ContractException unsupported(String message, String field, List<String> allowedFields) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowedFields", allowedFields)
        );
    }

    private String currentUserId() {
        return cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
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
        return "system/files/" + fileId + extension;
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

    private String buildDownloadUrl(String fileId) {
        return "/api/v1/system/files/" + fileId + "/download";
    }

    private String buildPreviewUrl(String fileId) {
        return "/api/v1/system/files/" + fileId + "/preview";
    }

    private record Filters(Boolean deleted) {
    }
}
