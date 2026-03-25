package com.westflow.workflowadmin.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.workflowadmin.api.response.ApprovalOpinionConfigDetailResponse;
import com.westflow.workflowadmin.api.response.ApprovalOpinionConfigFormOptionsResponse;
import com.westflow.workflowadmin.api.response.ApprovalOpinionConfigListItemResponse;
import com.westflow.workflowadmin.api.response.ApprovalOpinionConfigMutationResponse;
import com.westflow.workflowadmin.api.request.SaveApprovalOpinionConfigRequest;
import com.westflow.workflowadmin.mapper.ApprovalOpinionConfigMapper;
import com.westflow.workflowadmin.model.ApprovalOpinionConfigRecord;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 审批意见配置后台服务。
 */
@Service
@RequiredArgsConstructor
public class ApprovalOpinionConfigService {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST = new TypeReference<>() {
    };

    private final ApprovalOpinionConfigMapper approvalOpinionConfigMapper;
    private final IdentityAuthService fixtureAuthService;
    private final ObjectMapper objectMapper;

    public PageResponse<ApprovalOpinionConfigListItemResponse> page(PageRequest request) {
        ensureWorkflowAdminAccess();
        Boolean enabledFilter = resolveEnabledFilter(request.filters());
        List<ApprovalOpinionConfigListItemResponse> matched = approvalOpinionConfigMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> enabledFilter == null || enabledFilter == record.enabled())
                .sorted(Comparator.comparing(ApprovalOpinionConfigRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toListItem)
                .toList();
        return toPage(request, matched);
    }

    public ApprovalOpinionConfigDetailResponse detail(String configId) {
        ensureWorkflowAdminAccess();
        return toDetail(requireConfig(configId));
    }

    public ApprovalOpinionConfigFormOptionsResponse formOptions() {
        ensureWorkflowAdminAccess();
        return new ApprovalOpinionConfigFormOptionsResponse(
                List.of(
                        new ApprovalOpinionConfigFormOptionsResponse.ActionTypeOption("APPROVE", "同意"),
                        new ApprovalOpinionConfigFormOptionsResponse.ActionTypeOption("REJECT", "拒绝"),
                        new ApprovalOpinionConfigFormOptionsResponse.ActionTypeOption("RETURN", "退回"),
                        new ApprovalOpinionConfigFormOptionsResponse.ActionTypeOption("TRANSFER", "转办"),
                        new ApprovalOpinionConfigFormOptionsResponse.ActionTypeOption("DELEGATE", "委派")
                ),
                List.of(
                        new ApprovalOpinionConfigFormOptionsResponse.ToolbarActionOption("quickOpinion", "快捷意见"),
                        new ApprovalOpinionConfigFormOptionsResponse.ToolbarActionOption("mention", "提及人员"),
                        new ApprovalOpinionConfigFormOptionsResponse.ToolbarActionOption("attachment", "上传附件"),
                        new ApprovalOpinionConfigFormOptionsResponse.ToolbarActionOption("history", "历史意见")
                )
        );
    }

    @Transactional
    public ApprovalOpinionConfigMutationResponse create(SaveApprovalOpinionConfigRequest request) {
        ensureWorkflowAdminAccess();
        validateCode(request.configCode(), null);
        String configId = buildId("opcfg");
        Instant now = Instant.now();
        approvalOpinionConfigMapper.insert(new ApprovalOpinionConfigRecord(
                configId,
                normalize(request.configCode()),
                normalize(request.configName()),
                request.enabled(),
                writeValue(defaultIfNull(request.quickOpinions())),
                writeValue(defaultIfNull(request.toolbarActions())),
                writeValue(defaultButtonStrategies(request.buttonStrategies())),
                normalizeNullable(request.remark()),
                now,
                now
        ));
        return new ApprovalOpinionConfigMutationResponse(configId);
    }

    @Transactional
    public ApprovalOpinionConfigMutationResponse update(String configId, SaveApprovalOpinionConfigRequest request) {
        ensureWorkflowAdminAccess();
        ApprovalOpinionConfigRecord existing = requireConfig(configId);
        validateCode(request.configCode(), configId);
        approvalOpinionConfigMapper.update(new ApprovalOpinionConfigRecord(
                configId,
                normalize(request.configCode()),
                normalize(request.configName()),
                request.enabled(),
                writeValue(defaultIfNull(request.quickOpinions())),
                writeValue(defaultIfNull(request.toolbarActions())),
                writeValue(defaultButtonStrategies(request.buttonStrategies())),
                normalizeNullable(request.remark()),
                existing.createdAt(),
                Instant.now()
        ));
        return new ApprovalOpinionConfigMutationResponse(configId);
    }

    private ApprovalOpinionConfigListItemResponse toListItem(ApprovalOpinionConfigRecord record) {
        return new ApprovalOpinionConfigListItemResponse(
                record.configId(),
                record.configCode(),
                record.configName(),
                record.enabled() ? "ENABLED" : "DISABLED",
                readStringList(record.quickOpinionsJson()).size(),
                record.updatedAt()
        );
    }

    private ApprovalOpinionConfigDetailResponse toDetail(ApprovalOpinionConfigRecord record) {
        return new ApprovalOpinionConfigDetailResponse(
                record.configId(),
                record.configCode(),
                record.configName(),
                record.enabled() ? "ENABLED" : "DISABLED",
                readStringList(record.quickOpinionsJson()),
                readStringList(record.toolbarActionsJson()),
                readButtonStrategies(record.buttonStrategiesJson()),
                record.remark(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private List<ApprovalOpinionConfigDetailResponse.ButtonStrategy> readButtonStrategies(String json) {
        try {
            return objectMapper.readValue(json, MAP_LIST).stream()
                    .map(item -> new ApprovalOpinionConfigDetailResponse.ButtonStrategy(
                            String.valueOf(item.get("actionType")),
                            Boolean.TRUE.equals(item.get("requireOpinion"))
                    ))
                    .toList();
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法解析按钮策略配置", exception);
        }
    }

    private List<String> readStringList(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法解析字符串列表配置", exception);
        }
    }

    private String writeValue(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化审批意见配置", exception);
        }
    }

    private List<Map<String, Object>> defaultButtonStrategies(List<SaveApprovalOpinionConfigRequest.ButtonStrategy> value) {
        return value == null ? List.of() : value.stream()
                .map(item -> {
                    Map<String, Object> payload = new java.util.LinkedHashMap<>();
                    payload.put("actionType", item.actionType());
                    payload.put("requireOpinion", item.requireOpinion());
                    return payload;
                })
                .toList();
    }

    private List<String> defaultIfNull(List<String> value) {
        return value == null ? List.of() : value.stream().map(String::trim).filter(item -> !item.isBlank()).toList();
    }

    private boolean matchesKeyword(ApprovalOpinionConfigRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(record.configCode(), normalized)
                || contains(record.configName(), normalized)
                || contains(record.remark(), normalized);
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
    }

    private Boolean resolveEnabledFilter(List<FilterItem> filters) {
        for (FilterItem filter : filters) {
            if (!"status".equals(filter.field()) || !"eq".equalsIgnoreCase(filter.operator())) {
                continue;
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("ENABLED".equalsIgnoreCase(value)) {
                return true;
            }
            if ("DISABLED".equalsIgnoreCase(value)) {
                return false;
            }
        }
        return null;
    }

    private PageResponse<ApprovalOpinionConfigListItemResponse> toPage(PageRequest request, List<ApprovalOpinionConfigListItemResponse> matched) {
        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<ApprovalOpinionConfigListItemResponse> records = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex);
        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    private void validateCode(String configCode, String excludeConfigId) {
        if (approvalOpinionConfigMapper.existsByCode(normalize(configCode), excludeConfigId)) {
            throw new ContractException(
                    "BIZ.APPROVAL_OPINION_CONFIG_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "审批意见配置编码已存在",
                    Map.of("configCode", configCode)
            );
        }
    }

    private ApprovalOpinionConfigRecord requireConfig(String configId) {
        ApprovalOpinionConfigRecord record = approvalOpinionConfigMapper.selectById(configId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "审批意见配置不存在",
                    Map.of("configId", configId)
            );
        }
        return record;
    }

    private void ensureWorkflowAdminAccess() {
        String userId = cn.dev33.satoken.stp.StpUtil.getLoginIdAsString();
        if (!fixtureAuthService.isProcessAdmin(userId) && !fixtureAuthService.isSystemAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅流程管理员可以访问审批意见配置",
                    Map.of("userId", userId)
            );
        }
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private String normalize(String value) {
        String normalized = normalizeNullable(value);
        if (normalized == null) {
            throw new ContractException("VALIDATION.REQUEST_INVALID", HttpStatus.BAD_REQUEST, "参数不能为空");
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isBlank() ? null : normalized;
    }
}
