package com.westflow.system.org.post.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.CurrentUserAccessService;
import com.westflow.identity.service.CurrentUserAccessService.AccessPolicy;
import com.westflow.system.org.department.response.SystemDepartmentDetailResponse;
import com.westflow.system.org.department.mapper.SystemDepartmentMapper;
import com.westflow.system.org.post.request.SaveSystemPostRequest;
import com.westflow.system.org.post.response.SystemPostDetailResponse;
import com.westflow.system.org.post.response.SystemPostFormOptionsResponse;
import com.westflow.system.org.post.response.SystemPostListItemResponse;
import com.westflow.system.org.post.response.SystemPostMutationResponse;
import com.westflow.system.org.post.mapper.SystemPostMapper;
import com.westflow.system.org.post.model.SystemPostRecord;
import com.westflow.system.user.response.SystemAssociatedUserResponse;
import com.westflow.system.user.service.SystemUserService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统岗位管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemPostService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "companyId", "departmentId");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "postName", "departmentName", "companyName");

    private final SystemPostMapper systemPostMapper;
    private final SystemDepartmentMapper systemDepartmentMapper;
    private final CurrentUserAccessService currentUserAccessService;
    private final SystemUserService systemUserService;

    /**
     * 分页查询岗位。
     */
    public PageResponse<SystemPostListItemResponse> page(PageRequest request) {
        // 岗位列表先按当前人的组织可见范围过滤，再叠加关键词和筛选条件。
        AccessPolicy accessPolicy = currentUserAccessService.resolveAccessPolicy();
        Filters filters = resolveFilters(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        if (accessPolicy.restricted() && accessPolicy.isEmpty()) {
            return new PageResponse<>(request.page(), request.pageSize(), 0, 0, List.of(), List.of());
        }
        long total = systemPostMapper.countPage(
                request.keyword(),
                filters.enabled(),
                filters.companyId(),
                filters.departmentId(),
                accessPolicy.allAccess(),
                accessPolicy.companyIds(),
                accessPolicy.departmentIds()
        );
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<SystemPostListItemResponse> records = total == 0
                ? List.of()
                : systemPostMapper.selectPage(
                        request.keyword(),
                        filters.enabled(),
                        filters.companyId(),
                        filters.departmentId(),
                        accessPolicy.allAccess(),
                        accessPolicy.companyIds(),
                        accessPolicy.departmentIds(),
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                );

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    /**
     * 查询岗位详情。
     */
    public SystemPostDetailResponse detail(String postId) {
        // 岗位详情不能只看列表结果，要再次校验可见范围。
        SystemPostDetailResponse detail = systemPostMapper.selectDetail(postId);
        if (detail == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "岗位不存在",
                    Map.of("postId", postId)
            );
        }
        assertAccessible(detail.companyId(), detail.departmentId());
        return detail;
    }

    /**
     * 查询岗位关联用户。
     */
    public List<SystemAssociatedUserResponse> relatedUsers(String postId) {
        SystemPostDetailResponse detail = detail(postId);
        assertAccessible(detail.companyId(), detail.departmentId());
        return systemUserService.listAssociatedUsersByPostId(postId);
    }

    /**
     * 获取岗位表单选项。
     */
    public SystemPostFormOptionsResponse formOptions(String companyId) {
        return new SystemPostFormOptionsResponse(systemPostMapper.selectDepartmentOptions(companyId));
    }

    /**
     * 新建岗位。
     */
    @Transactional
    public SystemPostMutationResponse create(SaveSystemPostRequest request) {
        SystemDepartmentDetailResponse department = validateDepartmentExists(request.departmentId());
        validatePostName(request.departmentId(), request.postName(), null);
        String postId = buildId("post");
        systemPostMapper.insertPost(new SystemPostRecord(
                postId,
                request.departmentId(),
                request.postName(),
                request.enabled()
        ));
        return new SystemPostMutationResponse(postId);
    }

    /**
     * 更新岗位。
     */
    @Transactional
    public SystemPostMutationResponse update(String postId, SaveSystemPostRequest request) {
        ensureExists(postId);
        validateDepartmentExists(request.departmentId());
        validatePostName(request.departmentId(), request.postName(), postId);
        systemPostMapper.updatePost(new SystemPostRecord(
                postId,
                request.departmentId(),
                request.postName(),
                request.enabled()
        ));
        return new SystemPostMutationResponse(postId);
    }

    /**
     * 删除岗位。
     */
    @Transactional
    public SystemPostMutationResponse delete(String postId) {
        SystemPostDetailResponse detail = detail(postId);
        long assignmentCount = systemPostMapper.countUserAssignmentsByPostId(postId);
        if (assignmentCount > 0) {
            throw new ContractException(
                    "BIZ.POST_DELETE_BLOCKED",
                    HttpStatus.CONFLICT,
                    "当前岗位仍有关联任职，无法删除",
                    Map.of("postId", postId, "assignmentCount", assignmentCount)
            );
        }
        systemPostMapper.deletePost(detail.postId());
        return new SystemPostMutationResponse(postId);
    }

    private SystemDepartmentDetailResponse validateDepartmentExists(String departmentId) {
        SystemDepartmentDetailResponse department = systemDepartmentMapper.selectDetail(departmentId);
        if (department == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "部门不存在",
                    Map.of("departmentId", departmentId)
            );
        }
        return department;
    }

    private void ensureExists(String postId) {
        if (systemPostMapper.selectDetail(postId) == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "岗位不存在",
                    Map.of("postId", postId)
            );
        }
    }

    private void validatePostName(String departmentId, String postName, String excludePostId) {
        Long total = systemPostMapper.countByPostName(departmentId, postName, excludePostId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.POST_NAME_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "岗位名称已存在",
                    Map.of("postName", postName)
            );
        }
    }

    private Filters resolveFilters(List<FilterItem> filters) {
        Boolean enabled = null;
        String companyId = null;
        String departmentId = null;

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
                case "departmentId" -> departmentId = value;
                default -> {
                }
            }
        }
        return new Filters(enabled, companyId, departmentId);
    }

    private String resolveOrderBy(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "p.created_at";
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        return switch (sort.field()) {
            case "postName" -> "p.post_name";
            case "departmentName" -> "d.department_name";
            case "companyName" -> "c.company_name";
            default -> "p.created_at";
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

    private void assertAccessible(String companyId, String departmentId) {
        AccessPolicy accessPolicy = currentUserAccessService.resolveAccessPolicy();
        if (accessPolicy.allAccess()
                || accessPolicy.companyIds().contains(companyId)
                || accessPolicy.departmentIds().contains(departmentId)) {
            return;
        }
        throw new ContractException(
                "AUTH.FORBIDDEN",
                HttpStatus.FORBIDDEN,
                "无权访问当前数据",
                Map.of("companyId", companyId, "departmentId", departmentId)
        );
    }

    public record Filters(
            Boolean enabled,
            String companyId,
            String departmentId
    ) {
    }

}
