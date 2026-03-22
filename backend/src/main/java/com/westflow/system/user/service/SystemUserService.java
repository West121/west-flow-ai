package com.westflow.system.user.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.identity.mapper.IdentityAccessMapper;
import com.westflow.system.user.api.SaveSystemUserRequest;
import com.westflow.system.user.api.SystemUserDetailResponse;
import com.westflow.system.user.api.SystemUserFormOptionsResponse;
import com.westflow.system.user.api.SystemUserListItemResponse;
import com.westflow.system.user.api.SystemUserMutationResponse;
import com.westflow.system.user.mapper.SystemUserMapper;
import com.westflow.system.user.mapper.SystemUserMapper.SystemUserBaseDetailRecord;
import com.westflow.system.user.mapper.SystemUserMapper.PostContext;
import com.westflow.system.user.mapper.SystemUserMapper.SystemUserRoleBinding;
import java.util.ArrayDeque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SystemUserService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "departmentId", "postId");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of(
            "createdAt",
            "displayName",
            "username",
            "departmentName",
            "postName"
    );

    private final SystemUserMapper systemUserMapper;
    private final IdentityAccessMapper identityAccessMapper;

    public SystemUserService(SystemUserMapper systemUserMapper, IdentityAccessMapper identityAccessMapper) {
        this.systemUserMapper = systemUserMapper;
        this.identityAccessMapper = identityAccessMapper;
    }

    public PageResponse<SystemUserListItemResponse> page(PageRequest request) {
        AccessPolicy accessPolicy = resolveAccessPolicy();
        Filters filters = resolveFilters(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        if (accessPolicy.restricted() && accessPolicy.isEmpty()) {
            return new PageResponse<>(request.page(), request.pageSize(), 0, 0, List.of(), List.of());
        }
        long total = systemUserMapper.countPage(
                request.keyword(),
                filters.enabled(),
                filters.departmentId(),
                filters.postId(),
                accessPolicy.allAccess(),
                accessPolicy.userIds(),
                accessPolicy.departmentIds(),
                accessPolicy.companyIds()
        );
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<SystemUserListItemResponse> records = total == 0
                ? List.of()
                : systemUserMapper.selectPage(
                        request.keyword(),
                        filters.enabled(),
                        filters.departmentId(),
                        filters.postId(),
                        accessPolicy.allAccess(),
                        accessPolicy.userIds(),
                        accessPolicy.departmentIds(),
                        accessPolicy.companyIds(),
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                );

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    public SystemUserDetailResponse detail(String userId) {
        SystemUserBaseDetailRecord detail = systemUserMapper.selectDetail(userId);
        if (detail == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "用户不存在",
                    Map.of("userId", userId)
            );
        }
        assertAccessible(detail.companyId(), detail.departmentId(), userId);
        return new SystemUserDetailResponse(
                detail.userId(),
                detail.displayName(),
                detail.username(),
                detail.mobile(),
                detail.email(),
                detail.companyId(),
                detail.companyName(),
                detail.departmentId(),
                detail.departmentName(),
                detail.postId(),
                detail.postName(),
                systemUserMapper.selectRoleIdsByUserId(userId),
                detail.enabled()
        );
    }

    public SystemUserFormOptionsResponse formOptions() {
        return new SystemUserFormOptionsResponse(
                systemUserMapper.selectCompanyOptions(),
                systemUserMapper.selectPostOptions(),
                systemUserMapper.selectRoleOptions()
        );
    }

    @Transactional
    public SystemUserMutationResponse create(SaveSystemUserRequest request) {
        PostContext postContext = resolvePostContext(request.primaryPostId());
        validateCompanyConsistency(request.companyId(), postContext.companyId());
        assertAccessible(postContext.companyId(), postContext.departmentId(), null);
        validateUsername(request.username(), null);
        String userId = buildId("usr");

        systemUserMapper.insertUser(new SystemUserEntity(
                userId,
                request.username(),
                request.displayName(),
                request.mobile(),
                request.email(),
                "",
                request.companyId(),
                postContext.departmentId(),
                request.primaryPostId(),
                request.enabled()
        ));
        systemUserMapper.insertUserPost(new SystemUserPostBinding(
                buildId("up"),
                userId,
                request.primaryPostId(),
                true
        ));
        replaceUserRoles(userId, request.roleIds());

        return new SystemUserMutationResponse(userId);
    }

    @Transactional
    public SystemUserMutationResponse update(String userId, SaveSystemUserRequest request) {
        detail(userId);
        PostContext postContext = resolvePostContext(request.primaryPostId());
        validateCompanyConsistency(request.companyId(), postContext.companyId());
        assertAccessible(postContext.companyId(), postContext.departmentId(), userId);
        validateUsername(request.username(), userId);

        systemUserMapper.updateUser(new SystemUserEntity(
                userId,
                request.username(),
                request.displayName(),
                request.mobile(),
                request.email(),
                "",
                request.companyId(),
                postContext.departmentId(),
                request.primaryPostId(),
                request.enabled()
        ));
        systemUserMapper.deleteUserPosts(userId);
        systemUserMapper.insertUserPost(new SystemUserPostBinding(
                buildId("up"),
                userId,
                request.primaryPostId(),
                true
        ));
        replaceUserRoles(userId, request.roleIds());

        return new SystemUserMutationResponse(userId);
    }

    private void validateUsername(String username, String excludeUserId) {
        Long total = systemUserMapper.countByUsername(username, excludeUserId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.USERNAME_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "登录账号已存在",
                    Map.of("username", username)
            );
        }
    }

    private void replaceUserRoles(String userId, List<String> roleIds) {
        List<String> normalizedRoleIds = normalizeRoleIds(roleIds);
        if (normalizedRoleIds.isEmpty()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "请至少选择一个角色"
            );
        }
        if (systemUserMapper.countExistingRoles(normalizedRoleIds) != normalizedRoleIds.size()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "存在无效的角色",
                    Map.of("roleIds", normalizedRoleIds)
            );
        }
        systemUserMapper.deleteUserRoles(userId);
        for (String roleId : normalizedRoleIds) {
            systemUserMapper.insertUserRole(new SystemUserRoleBinding(buildId("ur"), userId, roleId));
        }
    }

    private List<String> normalizeRoleIds(List<String> roleIds) {
        if (roleIds == null) {
            return List.of();
        }
        return roleIds.stream()
                .map(this::normalizeNullable)
                .filter(value -> value != null)
                .distinct()
                .toList();
    }

    private PostContext resolvePostContext(String postId) {
        PostContext postContext = systemUserMapper.selectPostContextByPostId(postId);
        if (postContext == null || postContext.departmentId() == null || postContext.companyId() == null) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "主岗位不存在",
                    Map.of("primaryPostId", postId)
            );
        }
        return postContext;
    }

    private void validateCompanyConsistency(String companyId, String postCompanyId) {
        if (!postCompanyId.equals(companyId)) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "公司与主岗位所属组织不一致",
                    Map.of("companyId", companyId)
            );
        }
    }

    private void assertAccessible(String companyId, String departmentId, String userId) {
        AccessPolicy accessPolicy = resolveAccessPolicy();
        if (accessPolicy.allAccess()) {
            return;
        }
        if (userId != null && accessPolicy.userIds().contains(userId)) {
            return;
        }
        if (departmentId != null && accessPolicy.departmentIds().contains(departmentId)) {
            return;
        }
        if (companyId != null && accessPolicy.companyIds().contains(companyId)) {
            return;
        }
        Map<String, Object> details = new LinkedHashMap<>();
        if (userId != null) {
            details.put("userId", userId);
        }
        if (companyId != null) {
            details.put("companyId", companyId);
        }
        if (departmentId != null) {
            details.put("departmentId", departmentId);
        }
        throw new ContractException(
                "AUTH.FORBIDDEN",
                HttpStatus.FORBIDDEN,
                "无权访问当前数据",
                details
        );
    }

    private AccessPolicy resolveAccessPolicy() {
        String loginId = StpUtil.getLoginIdAsString();
        List<CurrentUserResponse.DataScope> dataScopes = identityAccessMapper.selectDataScopesByUserId(loginId);
        boolean allAccess = false;
        LinkedHashSet<String> userIds = new LinkedHashSet<>();
        LinkedHashSet<String> departmentIds = new LinkedHashSet<>();
        LinkedHashSet<String> companyIds = new LinkedHashSet<>();

        for (CurrentUserResponse.DataScope dataScope : dataScopes) {
            switch (dataScope.scopeType()) {
                case "ALL" -> allAccess = true;
                case "SELF" -> userIds.add(loginId);
                case "DEPARTMENT" -> departmentIds.add(dataScope.scopeValue());
                case "DEPARTMENT_AND_CHILDREN" -> departmentIds.addAll(
                        resolveDepartmentIdsWithDescendants(dataScope.scopeValue())
                );
                case "COMPANY" -> companyIds.add(dataScope.scopeValue());
                default -> {
                }
            }
        }

        return new AccessPolicy(
                allAccess,
                List.copyOf(userIds),
                List.copyOf(departmentIds),
                List.copyOf(companyIds)
        );
    }

    private List<String> resolveDepartmentIdsWithDescendants(String rootDepartmentId) {
        LinkedHashSet<String> collected = new LinkedHashSet<>();
        Queue<String> queue = new ArrayDeque<>();
        queue.add(rootDepartmentId);

        while (!queue.isEmpty()) {
            String departmentId = queue.poll();
            if (!collected.add(departmentId)) {
                continue;
            }
            queue.addAll(systemUserMapper.selectDepartmentIdsByParentId(departmentId));
        }

        return List.copyOf(collected);
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String departmentId = null;
        String postId = null;

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
                case "departmentId" -> departmentId = value;
                case "postId" -> postId = value;
                default -> {
                }
            }
        }
        return new Filters(enabled, departmentId, postId);
    }

    private String resolveOrderBy(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "u.created_at";
        }

        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        return switch (sort.field()) {
            case "displayName" -> "u.display_name";
            case "username" -> "u.username";
            case "departmentName" -> "d.department_name";
            case "postName" -> "p.post_name";
            default -> "u.created_at";
        };
    }

    private String resolveOrderDirection(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "DESC";
        }
        String direction = sorts.get(0).direction();
        return "asc".equalsIgnoreCase(direction) ? "ASC" : "DESC";
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

    private String normalizeNullable(String value) {
        return value == null ? null : value.trim();
    }

    private record Filters(
            Boolean enabled,
            String departmentId,
            String postId
    ) {
    }

    private record AccessPolicy(
            boolean allAccess,
            List<String> userIds,
            List<String> departmentIds,
            List<String> companyIds
    ) {
        boolean restricted() {
            return !allAccess;
        }

        boolean isEmpty() {
            return userIds.isEmpty() && departmentIds.isEmpty() && companyIds.isEmpty();
        }
    }

    public record SystemUserEntity(
            String id,
            String username,
            String displayName,
            String mobile,
            String email,
            String avatar,
            String companyId,
            String activeDepartmentId,
            String activePostId,
            Boolean enabled
    ) {
    }

    public record SystemUserPostBinding(
            String id,
            String userId,
            String postId,
            Boolean primary
    ) {
    }
}
