package com.westflow.system.org.department.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.system.org.company.mapper.SystemCompanyMapper;
import com.westflow.system.org.department.api.SaveSystemDepartmentRequest;
import com.westflow.system.org.department.api.SystemDepartmentDetailResponse;
import com.westflow.system.org.department.api.SystemDepartmentFormOptionsResponse;
import com.westflow.system.org.department.api.SystemDepartmentListItemResponse;
import com.westflow.system.org.department.api.SystemDepartmentMutationResponse;
import com.westflow.system.org.department.mapper.SystemDepartmentMapper;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemDepartmentService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "companyId", "parentDepartmentId");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "departmentName", "companyName");

    private final SystemDepartmentMapper systemDepartmentMapper;
    private final SystemCompanyMapper systemCompanyMapper;

    public SystemDepartmentService(
            SystemDepartmentMapper systemDepartmentMapper,
            SystemCompanyMapper systemCompanyMapper
    ) {
        this.systemDepartmentMapper = systemDepartmentMapper;
        this.systemCompanyMapper = systemCompanyMapper;
    }

    public PageResponse<SystemDepartmentListItemResponse> page(PageRequest request) {
        Filters filters = resolveFilters(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        long total = systemDepartmentMapper.countPage(
                request.keyword(),
                filters.enabled(),
                filters.companyId(),
                filters.parentDepartmentId()
        );
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<SystemDepartmentListItemResponse> records = total == 0
                ? List.of()
                : systemDepartmentMapper.selectPage(
                        request.keyword(),
                        filters.enabled(),
                        filters.companyId(),
                        filters.parentDepartmentId(),
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                );

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    public SystemDepartmentDetailResponse detail(String departmentId) {
        SystemDepartmentDetailResponse detail = systemDepartmentMapper.selectDetail(departmentId);
        if (detail == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "部门不存在",
                    Map.of("departmentId", departmentId)
            );
        }
        return detail;
    }

    public SystemDepartmentFormOptionsResponse formOptions(String companyId) {
        return new SystemDepartmentFormOptionsResponse(
                systemDepartmentMapper.selectCompanyOptions(),
                systemDepartmentMapper.selectParentDepartmentOptions(companyId)
        );
    }

    @Transactional
    public SystemDepartmentMutationResponse create(SaveSystemDepartmentRequest request) {
        validateCompanyExists(request.companyId());
        validateParentDepartment(request.companyId(), request.parentDepartmentId(), null);
        validateDepartmentName(request.companyId(), request.parentDepartmentId(), request.departmentName(), null);

        String departmentId = buildId("dept");
        systemDepartmentMapper.insertDepartment(new SystemDepartmentEntity(
                departmentId,
                request.companyId(),
                normalizeParentId(request.parentDepartmentId()),
                request.departmentName(),
                request.enabled()
        ));
        return new SystemDepartmentMutationResponse(departmentId);
    }

    @Transactional
    public SystemDepartmentMutationResponse update(String departmentId, SaveSystemDepartmentRequest request) {
        detail(departmentId);
        validateCompanyExists(request.companyId());
        validateParentDepartment(request.companyId(), request.parentDepartmentId(), departmentId);
        validateDepartmentName(request.companyId(), request.parentDepartmentId(), request.departmentName(), departmentId);

        systemDepartmentMapper.updateDepartment(new SystemDepartmentEntity(
                departmentId,
                request.companyId(),
                normalizeParentId(request.parentDepartmentId()),
                request.departmentName(),
                request.enabled()
        ));
        return new SystemDepartmentMutationResponse(departmentId);
    }

    private void validateCompanyExists(String companyId) {
        if (systemCompanyMapper.selectDetail(companyId) == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "公司不存在",
                    Map.of("companyId", companyId)
            );
        }
    }

    private void validateParentDepartment(String companyId, String parentDepartmentId, String excludeDepartmentId) {
        if (parentDepartmentId == null || parentDepartmentId.isBlank()) {
            return;
        }
        if (parentDepartmentId.equals(excludeDepartmentId)) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "父部门不能选择自己",
                    Map.of("parentDepartmentId", parentDepartmentId)
            );
        }
        SystemDepartmentDetailResponse parent = systemDepartmentMapper.selectDetail(parentDepartmentId);
        if (parent == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "父部门不存在",
                    Map.of("parentDepartmentId", parentDepartmentId)
            );
        }
        if (!companyId.equals(parent.companyId())) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "父部门必须属于同一公司",
                    Map.of("companyId", companyId, "parentDepartmentId", parentDepartmentId)
            );
        }
    }

    private void validateDepartmentName(String companyId, String parentDepartmentId, String departmentName, String excludeDepartmentId) {
        Long total = systemDepartmentMapper.countByDepartmentName(
                companyId,
                normalizeParentId(parentDepartmentId),
                departmentName,
                excludeDepartmentId
        );
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.DEPARTMENT_NAME_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "部门名称已存在",
                    Map.of("departmentName", departmentName)
            );
        }
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String companyId = null;
        String parentDepartmentId = null;

        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> {
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
                case "companyId" -> companyId = value;
                case "parentDepartmentId" -> parentDepartmentId = value;
                default -> {
                }
            }
        }
        return new Filters(enabled, companyId, parentDepartmentId);
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
            case "departmentName" -> "d.department_name";
            case "companyName" -> "c.company_name";
            default -> "d.created_at";
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

    private String normalizeParentId(String parentDepartmentId) {
        return parentDepartmentId == null || parentDepartmentId.isBlank() ? null : parentDepartmentId;
    }

    private String buildId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
    }

    public record Filters(
            Boolean enabled,
            String companyId,
            String parentDepartmentId
    ) {
    }

    public record SystemDepartmentEntity(
            String id,
            String companyId,
            String parentDepartmentId,
            String departmentName,
            Boolean enabled
    ) {
    }
}
