package com.westflow.system.org.company.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.CurrentUserAccessService;
import com.westflow.identity.service.CurrentUserAccessService.AccessPolicy;
import com.westflow.system.org.company.api.SaveSystemCompanyRequest;
import com.westflow.system.org.company.api.SystemCompanyDetailResponse;
import com.westflow.system.org.company.api.SystemCompanyFormOptionsResponse;
import com.westflow.system.org.company.api.SystemCompanyListItemResponse;
import com.westflow.system.org.company.api.SystemCompanyMutationResponse;
import com.westflow.system.org.company.mapper.SystemCompanyMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemCompanyService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "companyName");

    private final SystemCompanyMapper systemCompanyMapper;
    private final CurrentUserAccessService currentUserAccessService;

    public SystemCompanyService(
            SystemCompanyMapper systemCompanyMapper,
            CurrentUserAccessService currentUserAccessService
    ) {
        this.systemCompanyMapper = systemCompanyMapper;
        this.currentUserAccessService = currentUserAccessService;
    }

    public PageResponse<SystemCompanyListItemResponse> page(PageRequest request) {
        AccessPolicy accessPolicy = currentUserAccessService.resolveAccessPolicy();
        Boolean enabled = resolveEnabledFilter(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        if (accessPolicy.restricted() && accessPolicy.companyViewIds().isEmpty()) {
            return new PageResponse<>(request.page(), request.pageSize(), 0, 0, List.of(), List.of());
        }
        long total = systemCompanyMapper.countPage(
                request.keyword(),
                enabled,
                accessPolicy.allAccess(),
                accessPolicy.companyViewIds()
        );
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<SystemCompanyListItemResponse> records = total == 0
                ? List.of()
                : systemCompanyMapper.selectPage(
                        request.keyword(),
                        enabled,
                        accessPolicy.allAccess(),
                        accessPolicy.companyViewIds(),
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                );

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    public SystemCompanyDetailResponse detail(String companyId) {
        SystemCompanyDetailResponse detail = systemCompanyMapper.selectDetail(companyId);
        if (detail == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "公司不存在",
                    Map.of("companyId", companyId)
            );
        }
        assertAccessible(detail.companyId());
        return detail;
    }

    public SystemCompanyFormOptionsResponse formOptions() {
        return new SystemCompanyFormOptionsResponse(systemCompanyMapper.selectCompanyOptions());
    }

    @Transactional
    public SystemCompanyMutationResponse create(SaveSystemCompanyRequest request) {
        validateCompanyName(request.companyName(), null);
        String companyId = buildId("cmp");
        systemCompanyMapper.insertCompany(new SystemCompanyEntity(companyId, request.companyName(), request.enabled()));
        return new SystemCompanyMutationResponse(companyId);
    }

    @Transactional
    public SystemCompanyMutationResponse update(String companyId, SaveSystemCompanyRequest request) {
        ensureExists(companyId);
        validateCompanyName(request.companyName(), companyId);
        systemCompanyMapper.updateCompany(new SystemCompanyEntity(companyId, request.companyName(), request.enabled()));
        return new SystemCompanyMutationResponse(companyId);
    }

    private void validateCompanyName(String companyName, String excludeCompanyId) {
        Long total = systemCompanyMapper.countByCompanyName(companyName, excludeCompanyId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.COMPANY_NAME_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "公司名称已存在",
                    Map.of("companyName", companyName)
            );
        }
    }

    private Boolean resolveEnabledFilter(List<FilterItem> filters) {
        Boolean enabled = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            if ("ENABLED".equals(value)) {
                enabled = true;
            } else if ("DISABLED".equals(value)) {
                enabled = false;
            } else {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "状态筛选值不合法",
                        Map.of("status", value)
                );
            }
        }
        return enabled;
    }

    private String resolveOrderBy(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "c.created_at";
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        return switch (sort.field()) {
            case "companyName" -> "c.company_name";
            default -> "c.created_at";
        };
    }

    private String resolveOrderDirection(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "DESC";
        }
        return "asc".equalsIgnoreCase(sorts.get(0).direction()) ? "ASC" : "DESC";
    }

    private ContractException unsupported(String message, String field, List<String> allowedFields) {
        return new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("field", field, "allowedFields", allowedFields)
        );
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    private void assertAccessible(String companyId) {
        AccessPolicy accessPolicy = currentUserAccessService.resolveAccessPolicy();
        if (accessPolicy.canAccessCompany(companyId)) {
            return;
        }
        throw new ContractException(
                "AUTH.FORBIDDEN",
                HttpStatus.FORBIDDEN,
                "无权访问当前数据",
                Map.of("companyId", companyId)
        );
    }

    private void ensureExists(String companyId) {
        if (systemCompanyMapper.selectDetail(companyId) == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "公司不存在",
                    Map.of("companyId", companyId)
            );
        }
    }

    public record SystemCompanyEntity(
            String id,
            String companyName,
            Boolean enabled
    ) {
    }
}
