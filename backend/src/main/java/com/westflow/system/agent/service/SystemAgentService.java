package com.westflow.system.agent.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.FixtureAuthService;
import com.westflow.system.agent.api.SaveSystemAgentRequest;
import com.westflow.system.agent.api.SystemAgentDetailResponse;
import com.westflow.system.agent.api.SystemAgentFormOptionsResponse;
import com.westflow.system.agent.api.SystemAgentListItemResponse;
import com.westflow.system.agent.api.SystemAgentMutationResponse;
import com.westflow.system.agent.mapper.SystemAgentMapper;
import com.westflow.system.user.mapper.SystemUserMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SystemAgentService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "principalDisplayName", "delegateDisplayName", "status");
    private static final List<String> SUPPORTED_STATUSES = List.of("ACTIVE", "DISABLED");

    private final SystemAgentMapper systemAgentMapper;
    private final SystemUserMapper systemUserMapper;
    private final FixtureAuthService fixtureAuthService;

    public PageResponse<SystemAgentListItemResponse> page(PageRequest request) {
        ensureProcessAdmin();
        // 代理关系列表只支持少量稳定筛选，避免把管理页做成复杂查询平台。
        String status = null;
        for (FilterItem filter : request.filters()) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            if ("status".equals(filter.field())) {
                status = resolveStatus(filter.value() == null ? null : filter.value().asText());
            }
        }

        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        long total = systemAgentMapper.countPage(request.keyword(), status);
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<SystemAgentListItemResponse> records = total == 0
                ? List.of()
                : systemAgentMapper.selectPage(request.keyword(), status, orderBy, orderDirection, pageSize, offset);

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    public SystemAgentDetailResponse detail(String agentId) {
        ensureProcessAdmin();
        SystemAgentDetailResponse detail = systemAgentMapper.selectDetail(agentId);
        if (detail == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "代理关系不存在",
                    Map.of("agentId", agentId)
            );
        }
        return detail;
    }

    public SystemAgentFormOptionsResponse formOptions() {
        ensureProcessAdmin();
        return new SystemAgentFormOptionsResponse(
                systemAgentMapper.selectUserOptions(),
                systemAgentMapper.selectUserOptions(),
                List.of(
                        new SystemAgentFormOptionsResponse.StatusOption("ACTIVE", "启用"),
                        new SystemAgentFormOptionsResponse.StatusOption("DISABLED", "停用")
                )
        );
    }

    @Transactional
    public SystemAgentMutationResponse create(SaveSystemAgentRequest request) {
        ensureProcessAdmin();
        String principalUserId = normalize(request.principalUserId());
        String delegateUserId = normalize(request.delegateUserId());
        String status = resolveStatus(request.status());
        validateUsers(principalUserId, delegateUserId);
        validateRelation(principalUserId, delegateUserId, null);

        SystemAgentEntity entity = new SystemAgentEntity(
                buildId("agt"),
                principalUserId,
                delegateUserId,
                status,
                normalizeNullable(request.remark())
        );
        systemAgentMapper.insertDelegation(entity);
        return new SystemAgentMutationResponse(entity.id());
    }

    @Transactional
    public SystemAgentMutationResponse update(String agentId, SaveSystemAgentRequest request) {
        ensureProcessAdmin();
        detail(agentId);
        String principalUserId = normalize(request.principalUserId());
        String delegateUserId = normalize(request.delegateUserId());
        String status = resolveStatus(request.status());
        validateUsers(principalUserId, delegateUserId);
        validateRelation(principalUserId, delegateUserId, agentId);

        systemAgentMapper.updateDelegation(new SystemAgentEntity(
                agentId,
                principalUserId,
                delegateUserId,
                status,
                normalizeNullable(request.remark())
        ));
        return new SystemAgentMutationResponse(agentId);
    }

    private void validateUsers(String principalUserId, String delegateUserId) {
        if (principalUserId.equals(delegateUserId)) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "委托人和代理人不能是同一个人",
                    Map.of("principalUserId", principalUserId, "delegateUserId", delegateUserId)
            );
        }
        requireEnabledUser(principalUserId, "principalUserId");
        requireEnabledUser(delegateUserId, "delegateUserId");
    }

    private void requireEnabledUser(String userId, String fieldName) {
        var user = systemUserMapper.selectDetail(userId);
        if (user == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "用户不存在",
                    Map.of("userId", userId)
            );
        }
        if (!user.enabled()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "用户已停用，不能配置代理关系",
                    Map.of(fieldName, userId)
            );
        }
    }

    private void validateRelation(String principalUserId, String delegateUserId, String excludeAgentId) {
        // 同一个委托人和代理人只保留一条关系，编辑时用当前主键排除自己。
        Long duplicated = systemAgentMapper.countByPrincipalAndDelegate(principalUserId, delegateUserId, excludeAgentId);
        if (duplicated != null && duplicated > 0) {
            throw new ContractException(
                    "BIZ.RELATION_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "代理关系已存在",
                    Map.of("principalUserId", principalUserId, "delegateUserId", delegateUserId)
            );
        }
    }

    private void ensureProcessAdmin() {
        // 系统侧代理关系属于管理员配置面，普通用户不能直接读写。
        if (!fixtureAuthService.isProcessAdmin(currentUserId())) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅流程管理员可以访问代理关系管理",
                    Map.of("userId", currentUserId())
            );
        }
    }

    private String resolveStatus(String status) {
        String normalized = normalize(status);
        if (SUPPORTED_STATUSES.contains(normalized)) {
            return normalized;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "代理状态不合法",
                Map.of("status", status, "allowedStatuses", SUPPORTED_STATUSES)
        );
    }

    private String resolveOrderBy(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "d.created_at";
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        return switch (sort.field()) {
            case "principalDisplayName" -> "pu.display_name";
            case "delegateDisplayName" -> "du.display_name";
            case "status" -> "d.status";
            default -> "d.created_at";
        };
    }

    private String resolveOrderDirection(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "DESC";
        }
        return "asc".equalsIgnoreCase(sorts.get(0).direction()) ? "ASC" : "DESC";
    }

    private String normalize(String value) {
        if (value == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "字段不能为空",
                    Map.of("field", "value")
            );
        }
        String normalized = value.trim();
        if (normalized.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "字段不能为空",
                    Map.of("value", value)
            );
        }
        return normalized;
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String currentUserId() {
        return StpUtil.getLoginIdAsString();
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private ContractException unsupported(String message, String field, List<String> allowedFields) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowedFields", allowedFields)
        );
    }

    public record SystemAgentEntity(
            String id,
            String principalUserId,
            String delegateUserId,
            String status,
            String remark
    ) {
    }
}
