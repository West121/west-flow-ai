package com.westflow.system.role.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.system.role.api.SaveSystemRoleRequest;
import com.westflow.system.role.api.SystemRoleDetailResponse;
import com.westflow.system.role.api.SystemRoleFormOptionsResponse;
import com.westflow.system.role.api.SystemRoleListItemResponse;
import com.westflow.system.role.api.SystemRoleMutationResponse;
import com.westflow.system.role.mapper.SystemRoleMapper;
import com.westflow.system.role.mapper.SystemRoleMapper.SystemRoleBaseDetailRecord;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统角色管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemRoleService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "roleCategory");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "roleName", "roleCode", "roleCategory");
    private static final List<String> SUPPORTED_ROLE_CATEGORIES = List.of("SYSTEM", "BUSINESS");
    private static final List<String> SUPPORTED_SCOPE_TYPES = List.of(
            "ALL",
            "SELF",
            "DEPARTMENT",
            "DEPARTMENT_AND_CHILDREN",
            "COMPANY"
    );

    private final SystemRoleMapper systemRoleMapper;

    /**
     * 分页查询角色。
     */
    public PageResponse<SystemRoleListItemResponse> page(PageRequest request) {
        // 角色列表支持关键词、状态和分类筛选。
        Filters filters = resolveFilters(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        long total = systemRoleMapper.countPage(request.keyword(), filters.enabled(), filters.roleCategory());
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<SystemRoleListItemResponse> records = total == 0
                ? List.of()
                : systemRoleMapper.selectPage(
                        request.keyword(),
                        filters.enabled(),
                        filters.roleCategory(),
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                ).stream().map(this::mapListItem).toList();

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    /**
     * 查询角色详情。
     */
    public SystemRoleDetailResponse detail(String roleId) {
        // 角色详情要把菜单和数据权限一起带回去，方便编辑页一次性渲染。
        SystemRoleBaseDetailRecord role = systemRoleMapper.selectRole(roleId);
        if (role == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "角色不存在",
                    Map.of("roleId", roleId)
            );
        }
        return new SystemRoleDetailResponse(
                role.roleId(),
                role.roleCode(),
                role.roleName(),
                role.roleCategory(),
                role.description(),
                systemRoleMapper.selectMenuIdsByRoleId(roleId),
                systemRoleMapper.selectDataScopesByRoleId(roleId),
                role.enabled()
        );
    }

    /**
     * 获取角色表单选项。
     */
    public SystemRoleFormOptionsResponse formOptions() {
        // 表单选项集中返回，前端无需再拆多个接口。
        return new SystemRoleFormOptionsResponse(
                systemRoleMapper.selectMenuOptions(),
                List.of(
                        new SystemRoleFormOptionsResponse.ScopeTypeOption("ALL", "全部数据"),
                        new SystemRoleFormOptionsResponse.ScopeTypeOption("SELF", "仅本人"),
                        new SystemRoleFormOptionsResponse.ScopeTypeOption("DEPARTMENT", "指定部门"),
                        new SystemRoleFormOptionsResponse.ScopeTypeOption("DEPARTMENT_AND_CHILDREN", "部门及子部门"),
                        new SystemRoleFormOptionsResponse.ScopeTypeOption("COMPANY", "指定公司")
                ),
                systemRoleMapper.selectCompanyOptions(),
                systemRoleMapper.selectDepartmentOptions(),
                systemRoleMapper.selectUserOptions()
        );
    }

    /**
     * 新建角色。
     */
    @Transactional
    public SystemRoleMutationResponse create(SaveSystemRoleRequest request) {
        validateRoleCode(request.roleCode(), null);
        validateRoleCategory(request.roleCategory());
        List<String> menuIds = normalizeMenuIds(request.menuIds());
        validateMenus(menuIds);
        List<RoleDataScopeEntity> dataScopes = normalizeDataScopes(null, request.dataScopes());

        String roleId = buildId("role");
        systemRoleMapper.insertRole(new SystemRoleEntity(
                roleId,
                request.roleCode().trim(),
                request.roleName().trim(),
                request.roleCategory().trim(),
                normalizeNullable(request.description()),
                request.enabled()
        ));
        replaceRoleBindings(roleId, menuIds, dataScopes);
        return new SystemRoleMutationResponse(roleId);
    }

    /**
     * 更新角色。
     */
    @Transactional
    public SystemRoleMutationResponse update(String roleId, SaveSystemRoleRequest request) {
        detail(roleId);
        validateRoleCode(request.roleCode(), roleId);
        validateRoleCategory(request.roleCategory());
        List<String> menuIds = normalizeMenuIds(request.menuIds());
        validateMenus(menuIds);
        List<RoleDataScopeEntity> dataScopes = normalizeDataScopes(roleId, request.dataScopes());

        systemRoleMapper.updateRole(new SystemRoleEntity(
                roleId,
                request.roleCode().trim(),
                request.roleName().trim(),
                request.roleCategory().trim(),
                normalizeNullable(request.description()),
                request.enabled()
        ));
        replaceRoleBindings(roleId, menuIds, dataScopes);
        return new SystemRoleMutationResponse(roleId);
    }

    private SystemRoleListItemResponse mapListItem(SystemRoleListItemResponse item) {
        return new SystemRoleListItemResponse(
                item.roleId(),
                item.roleCode(),
                item.roleName(),
                item.roleCategory(),
                resolveScopeSummary(item.dataScopeSummary()),
                item.menuCount(),
                item.status(),
                item.createdAt()
        );
    }

    private String resolveScopeSummary(String scopeType) {
        return switch (scopeType) {
            case "ALL" -> "全部数据";
            case "SELF" -> "仅本人";
            case "DEPARTMENT" -> "指定部门";
            case "DEPARTMENT_AND_CHILDREN" -> "部门及子部门";
            case "COMPANY" -> "指定公司";
            case "NONE" -> "未配置";
            default -> "多策略";
        };
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String roleCategory = null;
        for (FilterItem filter : filters) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }
            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> enabled = switch (value) {
                    case "ENABLED" -> true;
                    case "DISABLED" -> false;
                    default -> throw new ContractException(
                            "VALIDATION.REQUEST_INVALID",
                            HttpStatus.BAD_REQUEST,
                            "状态筛选值不合法",
                            Map.of("status", value)
                    );
                };
                case "roleCategory" -> roleCategory = validateRoleCategory(value);
                default -> {
                }
            }
        }
        return new Filters(enabled, roleCategory);
    }

    private void validateRoleCode(String roleCode, String excludeRoleId) {
        Long total = systemRoleMapper.countByRoleCode(roleCode.trim(), excludeRoleId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.ROLE_CODE_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "角色编码已存在",
                    Map.of("roleCode", roleCode)
            );
        }
    }

    private String validateRoleCategory(String roleCategory) {
        String normalized = normalizeNullable(roleCategory);
        if (normalized != null && SUPPORTED_ROLE_CATEGORIES.contains(normalized)) {
            return normalized;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "角色分类不合法",
                Map.of("roleCategory", roleCategory, "allowedCategories", SUPPORTED_ROLE_CATEGORIES)
        );
    }

    private List<String> normalizeMenuIds(List<String> menuIds) {
        List<String> normalized = menuIds.stream()
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .distinct()
                .toList();
        if (normalized.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "请至少选择一个菜单权限"
            );
        }
        return normalized;
    }

    private void validateMenus(List<String> menuIds) {
        if (systemRoleMapper.countExistingMenus(menuIds) != menuIds.size()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "存在无效的菜单权限",
                    Map.of("menuIds", menuIds)
            );
        }
    }

    private List<RoleDataScopeEntity> normalizeDataScopes(String roleId, List<SaveSystemRoleRequest.RoleDataScopeInput> dataScopes) {
        if (dataScopes == null || dataScopes.isEmpty()) {
            return List.of();
        }

        LinkedHashSet<String> uniqueKeys = new LinkedHashSet<>();
        return dataScopes.stream().map((scope) -> {
            String scopeType = validateScopeType(scope.scopeType());
            String scopeValue = normalizeScopeValue(scopeType, scope.scopeValue());
            String dedupKey = scopeType + "::" + scopeValue;
            if (!uniqueKeys.add(dedupKey)) {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "数据权限范围存在重复项",
                        Map.of("scopeType", scopeType, "scopeValue", scopeValue)
                );
            }
            validateScopeTarget(scopeType, scopeValue);
            return new RoleDataScopeEntity(buildId("rds"), roleId, scopeType, scopeValue);
        }).toList();
    }

    private String validateScopeType(String scopeType) {
        String normalized = normalizeNullable(scopeType);
        if (normalized != null && SUPPORTED_SCOPE_TYPES.contains(normalized)) {
            return normalized;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "数据权限类型不合法",
                Map.of("scopeType", scopeType, "allowedScopeTypes", SUPPORTED_SCOPE_TYPES)
        );
    }

    private String normalizeScopeValue(String scopeType, String scopeValue) {
        if ("ALL".equals(scopeType) || "SELF".equals(scopeType)) {
            return "*";
        }
        String normalized = normalizeNullable(scopeValue);
        if (normalized == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "数据权限范围不能为空",
                    Map.of("scopeType", scopeType)
            );
        }
        return normalized;
    }

    private void validateScopeTarget(String scopeType, String scopeValue) {
        if ("ALL".equals(scopeType) || "SELF".equals(scopeType)) {
            return;
        }

        boolean exists = switch (scopeType) {
            case "DEPARTMENT", "DEPARTMENT_AND_CHILDREN" -> systemRoleMapper.countDepartmentById(scopeValue) > 0;
            case "COMPANY" -> systemRoleMapper.countCompanyById(scopeValue) > 0;
            default -> false;
        };
        if (!exists) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "数据权限范围不存在",
                    Map.of("scopeType", scopeType, "scopeValue", scopeValue)
            );
        }
    }

    private void replaceRoleBindings(String roleId, List<String> menuIds, List<RoleDataScopeEntity> dataScopes) {
        // 先清后插，保持角色菜单和数据权限始终是最终态。
        systemRoleMapper.deleteRoleMenus(roleId);
        systemRoleMapper.deleteRoleDataScopes(roleId);
        menuIds.forEach((menuId) -> systemRoleMapper.insertRoleMenu(new RoleMenuBinding(buildId("rm"), roleId, menuId)));
        dataScopes.forEach((scope) -> systemRoleMapper.insertRoleDataScope(new RoleDataScopeEntity(
                scope.id(),
                roleId,
                scope.scopeType(),
                scope.scopeValue()
        )));
    }

    private String resolveOrderBy(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "r.created_at";
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        return switch (sort.field()) {
            case "roleName" -> "r.role_name";
            case "roleCode" -> "r.role_code";
            case "roleCategory" -> "r.role_category";
            default -> "r.created_at";
        };
    }

    private String resolveOrderDirection(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "DESC";
        }
        return "asc".equalsIgnoreCase(sorts.get(0).direction()) ? "ASC" : "DESC";
    }

    private String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
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

    private record Filters(
            Boolean enabled,
            String roleCategory
    ) {
    }

    public record SystemRoleEntity(
            String id,
            String roleCode,
            String roleName,
            String roleCategory,
            String description,
            Boolean enabled
    ) {
    }

    public record RoleMenuBinding(
            String id,
            String roleId,
            String menuId
    ) {
    }

    public record RoleDataScopeEntity(
            String id,
            String roleId,
            String scopeType,
            String scopeValue
    ) {
    }
}
