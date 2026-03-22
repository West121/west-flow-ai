package com.westflow.workflowadmin.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.processbinding.mapper.BusinessProcessBindingMapper;
import com.westflow.processbinding.model.BusinessProcessBindingRecord;
import com.westflow.processdef.mapper.ProcessDefinitionMapper;
import com.westflow.processdef.model.ProcessDefinitionRecord;
import com.westflow.workflowadmin.api.SaveWorkflowBindingRequest;
import com.westflow.workflowadmin.api.WorkflowBindingDetailResponse;
import com.westflow.workflowadmin.api.WorkflowBindingFormOptionsResponse;
import com.westflow.workflowadmin.api.WorkflowBindingListItemResponse;
import com.westflow.workflowadmin.api.WorkflowBindingMutationResponse;
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
 * 业务流程绑定后台服务。
 */
@Service
@RequiredArgsConstructor
public class WorkflowBindingService {

    private final BusinessProcessBindingMapper businessProcessBindingMapper;
    private final ProcessDefinitionMapper processDefinitionMapper;
    private final FixtureAuthService fixtureAuthService;

    public PageResponse<WorkflowBindingListItemResponse> page(PageRequest request) {
        ensureWorkflowAdminAccess();
        Filters filters = resolveFilters(request.filters());
        List<WorkflowBindingListItemResponse> matched = businessProcessBindingMapper.selectAll().stream()
                .filter(record -> matchesKeyword(record, request.keyword()))
                .filter(record -> filters.businessType() == null || filters.businessType().equalsIgnoreCase(record.businessType()))
                .filter(record -> filters.enabled() == null || filters.enabled().equals(record.enabled()))
                .sorted(Comparator.comparing(BusinessProcessBindingRecord::updatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .map(this::toListItem)
                .toList();
        return toPage(request, matched);
    }

    public WorkflowBindingDetailResponse detail(String bindingId) {
        ensureWorkflowAdminAccess();
        return toDetail(requireBinding(bindingId));
    }

    public WorkflowBindingFormOptionsResponse formOptions() {
        ensureWorkflowAdminAccess();
        List<WorkflowBindingFormOptionsResponse.BusinessTypeOption> businessTypes = List.of(
                new WorkflowBindingFormOptionsResponse.BusinessTypeOption("OA_LEAVE", "请假申请"),
                new WorkflowBindingFormOptionsResponse.BusinessTypeOption("OA_EXPENSE", "报销申请"),
                new WorkflowBindingFormOptionsResponse.BusinessTypeOption("OA_COMMON", "通用申请")
        );
        List<WorkflowBindingFormOptionsResponse.ProcessDefinitionOption> processDefinitions = processDefinitionMapper.selectAllPublished()
                .stream()
                .map(record -> new WorkflowBindingFormOptionsResponse.ProcessDefinitionOption(
                        record.processDefinitionId(),
                        record.processKey(),
                        record.processName(),
                        record.version()
                ))
                .toList();
        return new WorkflowBindingFormOptionsResponse(businessTypes, processDefinitions);
    }

    @Transactional
    public WorkflowBindingMutationResponse create(SaveWorkflowBindingRequest request) {
        ensureWorkflowAdminAccess();
        validateBusinessScene(request.businessType(), request.sceneCode(), null);
        ProcessDefinitionRecord definition = requirePublishedDefinition(request.processDefinitionId(), request.processKey());
        String bindingId = buildId("wfbind");
        Instant now = Instant.now();
        businessProcessBindingMapper.insert(new BusinessProcessBindingRecord(
                bindingId,
                normalize(request.businessType()),
                normalize(request.sceneCode()),
                definition.processKey(),
                definition.processDefinitionId(),
                request.enabled(),
                request.priority(),
                now,
                now
        ));
        return new WorkflowBindingMutationResponse(bindingId);
    }

    @Transactional
    public WorkflowBindingMutationResponse update(String bindingId, SaveWorkflowBindingRequest request) {
        ensureWorkflowAdminAccess();
        BusinessProcessBindingRecord existing = requireBinding(bindingId);
        validateBusinessScene(request.businessType(), request.sceneCode(), bindingId);
        ProcessDefinitionRecord definition = requirePublishedDefinition(request.processDefinitionId(), request.processKey());
        businessProcessBindingMapper.update(new BusinessProcessBindingRecord(
                existing.id(),
                normalize(request.businessType()),
                normalize(request.sceneCode()),
                definition.processKey(),
                definition.processDefinitionId(),
                request.enabled(),
                request.priority(),
                existing.createdAt(),
                Instant.now()
        ));
        return new WorkflowBindingMutationResponse(bindingId);
    }

    private WorkflowBindingListItemResponse toListItem(BusinessProcessBindingRecord record) {
        ProcessDefinitionRecord definition = resolveDefinition(record.processDefinitionId(), record.processKey());
        return new WorkflowBindingListItemResponse(
                record.id(),
                record.businessType(),
                record.sceneCode(),
                record.processKey(),
                record.processDefinitionId(),
                definition == null ? record.processKey() : definition.processName(),
                Boolean.TRUE.equals(record.enabled()),
                record.priority() == null ? 0 : record.priority(),
                record.updatedAt()
        );
    }

    private WorkflowBindingDetailResponse toDetail(BusinessProcessBindingRecord record) {
        ProcessDefinitionRecord definition = resolveDefinition(record.processDefinitionId(), record.processKey());
        return new WorkflowBindingDetailResponse(
                record.id(),
                record.businessType(),
                record.sceneCode(),
                record.processKey(),
                record.processDefinitionId(),
                definition == null ? record.processKey() : definition.processName(),
                Boolean.TRUE.equals(record.enabled()),
                record.priority() == null ? 0 : record.priority(),
                record.createdAt(),
                record.updatedAt()
        );
    }

    private ProcessDefinitionRecord requirePublishedDefinition(String processDefinitionId, String processKey) {
        ProcessDefinitionRecord definition = null;
        if (processDefinitionId != null && !processDefinitionId.isBlank()) {
            definition = processDefinitionMapper.selectById(processDefinitionId);
        } else if (processKey != null && !processKey.isBlank()) {
            definition = processDefinitionMapper.selectLatestPublishedByProcessKey(processKey);
        }
        if (definition == null || !"PUBLISHED".equals(definition.status())) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "可用流程定义不存在",
                    Map.of("processDefinitionId", processDefinitionId, "processKey", processKey)
            );
        }
        return definition;
    }

    private ProcessDefinitionRecord resolveDefinition(String processDefinitionId, String processKey) {
        if (processDefinitionId != null && !processDefinitionId.isBlank()) {
            return processDefinitionMapper.selectById(processDefinitionId);
        }
        if (processKey != null && !processKey.isBlank()) {
            return processDefinitionMapper.selectLatestPublishedByProcessKey(processKey);
        }
        return null;
    }

    private void validateBusinessScene(String businessType, String sceneCode, String excludeBindingId) {
        if (businessProcessBindingMapper.existsByBusinessScene(normalize(businessType), normalize(sceneCode), excludeBindingId)) {
            throw new ContractException(
                    "BIZ.BUSINESS_PROCESS_BINDING_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "同一业务类型和场景码只能存在一条绑定",
                    Map.of("businessType", businessType, "sceneCode", sceneCode)
            );
        }
    }

    private boolean matchesKeyword(BusinessProcessBindingRecord record, String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return true;
        }
        String normalized = keyword.trim().toLowerCase();
        return contains(record.businessType(), normalized)
                || contains(record.sceneCode(), normalized)
                || contains(record.processKey(), normalized)
                || contains(record.processDefinitionId(), normalized);
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        String businessType = null;
        Boolean enabled = null;
        for (FilterItem filter : filters) {
            if (!"eq".equalsIgnoreCase(filter.operator())) {
                continue;
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("businessType".equals(filter.field())) {
                businessType = normalizeNullable(value);
            }
            if ("status".equals(filter.field())) {
                enabled = "ENABLED".equalsIgnoreCase(value) ? Boolean.TRUE : "DISABLED".equalsIgnoreCase(value) ? Boolean.FALSE : null;
            }
        }
        return new Filters(businessType, enabled);
    }

    private PageResponse<WorkflowBindingListItemResponse> toPage(PageRequest request, List<WorkflowBindingListItemResponse> matched) {
        long total = matched.size();
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        int fromIndex = Math.max(0, (request.page() - 1) * request.pageSize());
        int toIndex = Math.min(matched.size(), fromIndex + request.pageSize());
        List<WorkflowBindingListItemResponse> records = fromIndex >= matched.size()
                ? List.of()
                : matched.subList(fromIndex, toIndex);
        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    private BusinessProcessBindingRecord requireBinding(String bindingId) {
        BusinessProcessBindingRecord record = businessProcessBindingMapper.selectById(bindingId);
        if (record == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "业务流程绑定不存在",
                    Map.of("bindingId", bindingId)
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
                    "仅流程管理员可以访问业务流程绑定",
                    Map.of("userId", userId)
            );
        }
    }

    private boolean contains(String value, String keyword) {
        return value != null && value.toLowerCase().contains(keyword);
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

    private record Filters(String businessType, Boolean enabled) {
    }
}
