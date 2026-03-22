package com.westflow.system.user.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.system.user.api.SaveSystemUserRequest;
import com.westflow.system.user.api.SystemUserDetailResponse;
import com.westflow.system.user.api.SystemUserFormOptionsResponse;
import com.westflow.system.user.api.SystemUserListItemResponse;
import com.westflow.system.user.api.SystemUserMutationResponse;
import com.westflow.system.user.mapper.SystemUserMapper;
import java.util.List;
import java.util.Map;
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

    public SystemUserService(SystemUserMapper systemUserMapper) {
        this.systemUserMapper = systemUserMapper;
    }

    public PageResponse<SystemUserListItemResponse> page(PageRequest request) {
        Filters filters = resolveFilters(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        long total = systemUserMapper.countPage(
                request.keyword(),
                filters.enabled(),
                filters.departmentId(),
                filters.postId()
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
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                );

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    public SystemUserDetailResponse detail(String userId) {
        SystemUserDetailResponse detail = systemUserMapper.selectDetail(userId);
        if (detail == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "用户不存在",
                    Map.of("userId", userId)
            );
        }
        return detail;
    }

    public SystemUserFormOptionsResponse formOptions() {
        return new SystemUserFormOptionsResponse(
                systemUserMapper.selectCompanyOptions(),
                systemUserMapper.selectPostOptions()
        );
    }

    @Transactional
    public SystemUserMutationResponse create(SaveSystemUserRequest request) {
        validateUsername(request.username(), null);
        String departmentId = resolveDepartmentId(request.primaryPostId());
        String userId = buildId("usr");

        systemUserMapper.insertUser(new SystemUserEntity(
                userId,
                request.username(),
                request.displayName(),
                request.mobile(),
                request.email(),
                "",
                request.companyId(),
                departmentId,
                request.primaryPostId(),
                request.enabled()
        ));
        systemUserMapper.insertUserPost(new SystemUserPostBinding(
                buildId("up"),
                userId,
                request.primaryPostId(),
                true
        ));

        return new SystemUserMutationResponse(userId);
    }

    @Transactional
    public SystemUserMutationResponse update(String userId, SaveSystemUserRequest request) {
        detail(userId);
        validateUsername(request.username(), userId);
        String departmentId = resolveDepartmentId(request.primaryPostId());

        systemUserMapper.updateUser(new SystemUserEntity(
                userId,
                request.username(),
                request.displayName(),
                request.mobile(),
                request.email(),
                "",
                request.companyId(),
                departmentId,
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

    private String resolveDepartmentId(String postId) {
        String departmentId = systemUserMapper.selectDepartmentIdByPostId(postId);
        if (departmentId == null || departmentId.isBlank()) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "主岗位不存在",
                    Map.of("primaryPostId", postId)
            );
        }
        return departmentId;
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

    private record Filters(
            Boolean enabled,
            String departmentId,
            String postId
    ) {
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
