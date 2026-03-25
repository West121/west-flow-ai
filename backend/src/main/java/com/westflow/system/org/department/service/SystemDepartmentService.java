package com.westflow.system.org.department.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.identity.service.CurrentUserAccessService;
import com.westflow.identity.service.CurrentUserAccessService.AccessPolicy;
import com.westflow.system.org.company.mapper.SystemCompanyMapper;
import com.westflow.system.org.department.mapper.SystemDepartmentMapper;
import com.westflow.system.org.department.model.SystemDepartmentRecord;
import com.westflow.system.org.department.request.SaveSystemDepartmentRequest;
import com.westflow.system.org.department.response.SystemDepartmentDetailResponse;
import com.westflow.system.org.department.response.SystemDepartmentFormOptionsResponse;
import com.westflow.system.org.department.response.SystemDepartmentListItemResponse;
import com.westflow.system.org.department.response.SystemDepartmentMutationResponse;
import com.westflow.system.org.department.response.SystemDepartmentTreeNodeResponse;
import com.westflow.system.user.response.SystemAssociatedUserResponse;
import com.westflow.system.user.service.SystemUserService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统部门管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemDepartmentService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "companyId", "parentDepartmentId", "rootDepartmentId");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "departmentName", "companyName", "treeLevel");

    private final SystemDepartmentMapper systemDepartmentMapper;
    private final SystemCompanyMapper systemCompanyMapper;
    private final CurrentUserAccessService currentUserAccessService;
    private final SystemUserService systemUserService;

    /**
     * 分页查询部门。
     */
    public PageResponse<SystemDepartmentListItemResponse> page(PageRequest request) {
        AccessPolicy accessPolicy = currentUserAccessService.resolveAccessPolicy();
        Filters filters = resolveFilters(request.filters());
        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        if (accessPolicy.restricted() && accessPolicy.isEmpty()) {
            return new PageResponse<>(request.page(), request.pageSize(), 0, 0, List.of(), List.of());
        }

        long total = systemDepartmentMapper.countPage(
                request.keyword(),
                filters.enabled(),
                filters.companyId(),
                filters.parentDepartmentId(),
                filters.rootDepartmentId(),
                accessPolicy.allAccess(),
                accessPolicy.companyIds(),
                accessPolicy.departmentIds()
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
                        filters.rootDepartmentId(),
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
     * 查询部门树。
     */
    public List<SystemDepartmentTreeNodeResponse> tree(String companyId, Boolean enabled) {
        AccessPolicy accessPolicy = currentUserAccessService.resolveAccessPolicy();
        if (accessPolicy.restricted() && accessPolicy.isEmpty()) {
            return List.of();
        }
        List<SystemDepartmentDetailResponse> flatNodes = systemDepartmentMapper.selectTree(
                companyId,
                enabled,
                accessPolicy.allAccess(),
                accessPolicy.companyIds(),
                accessPolicy.departmentIds()
        );
        return buildTree(flatNodes);
    }

    /**
     * 启动后回填已有部门的树元数据。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void backfillTreeMetadata() {
        List<SystemDepartmentDetailResponse> departments = systemDepartmentMapper.selectTree(
                null,
                null,
                true,
                List.of(),
                List.of()
        );
        if (departments.isEmpty()) {
            return;
        }
        boolean needsBackfill = departments.stream().anyMatch(department ->
                department.rootDepartmentId() == null
                        || department.treeLevel() == null
                        || department.treePath() == null
        );
        if (!needsBackfill) {
            return;
        }

        Map<String, SystemDepartmentDetailResponse> departmentMap = new LinkedHashMap<>();
        for (SystemDepartmentDetailResponse department : departments) {
            departmentMap.put(department.departmentId(), department);
        }

        Map<String, DepartmentTreeMeta> resolvedMeta = new HashMap<>();
        for (SystemDepartmentDetailResponse department : departments) {
            DepartmentTreeMeta treeMeta = resolveTreeMetaForBackfill(
                    department.departmentId(),
                    departmentMap,
                    resolvedMeta,
                    new HashSet<>()
            );
            if (Objects.equals(department.rootDepartmentId(), treeMeta.rootDepartmentId())
                    && Objects.equals(department.treeLevel(), treeMeta.treeLevel())
                    && Objects.equals(department.treePath(), treeMeta.treePath())) {
                continue;
            }
            systemDepartmentMapper.updateDepartment(new SystemDepartmentRecord(
                    department.departmentId(),
                    department.companyId(),
                    normalizeParentId(department.parentDepartmentId()),
                    treeMeta.rootDepartmentId(),
                    treeMeta.treeLevel(),
                    treeMeta.treePath(),
                    department.departmentName(),
                    department.enabled()
            ));
        }
    }

    /**
     * 查询部门祖先链。
     */
    public List<SystemDepartmentDetailResponse> ancestors(String departmentId) {
        SystemDepartmentDetailResponse detail = detail(departmentId);
        List<String> ancestorIds = splitTreePath(detail.treePath());
        if (ancestorIds.isEmpty()) {
            return List.of(detail);
        }
        return systemDepartmentMapper.selectDepartmentsByIds(ancestorIds);
    }

    /**
     * 查询部门及以下的子树。
     */
    public SystemDepartmentTreeNodeResponse subtree(String departmentId) {
        SystemDepartmentDetailResponse detail = detail(departmentId);
        AccessPolicy accessPolicy = currentUserAccessService.resolveAccessPolicy();
        List<SystemDepartmentDetailResponse> flatNodes = systemDepartmentMapper.selectSubtree(
                detail.treePath(),
                accessPolicy.allAccess(),
                accessPolicy.companyIds(),
                accessPolicy.departmentIds()
        );
        List<SystemDepartmentTreeNodeResponse> tree = buildTree(flatNodes);
        if (tree.isEmpty()) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "部门不存在",
                    Map.of("departmentId", departmentId)
            );
        }
        return tree.get(0);
    }

    /**
     * 查询部门详情。
     */
    public SystemDepartmentDetailResponse detail(String departmentId) {
        SystemDepartmentDetailResponse detail = loadDepartment(departmentId);
        assertAccessible(detail.companyId(), detail.departmentId());
        return detail;
    }

    /**
     * 查询部门关联用户。
     */
    public List<SystemAssociatedUserResponse> relatedUsers(String departmentId) {
        SystemDepartmentDetailResponse detail = detail(departmentId);
        assertAccessible(detail.companyId(), detail.departmentId());
        return systemUserService.listAssociatedUsersByDepartmentId(departmentId);
    }

    /**
     * 获取部门表单选项。
     */
    public SystemDepartmentFormOptionsResponse formOptions(String companyId) {
        return new SystemDepartmentFormOptionsResponse(
                systemDepartmentMapper.selectCompanyOptions(),
                systemDepartmentMapper.selectParentDepartmentOptions(companyId)
        );
    }

    /**
     * 新建部门。
     */
    @Transactional
    public SystemDepartmentMutationResponse create(SaveSystemDepartmentRequest request) {
        validateCompanyExists(request.companyId());
        SystemDepartmentDetailResponse parent = validateParentDepartment(
                request.companyId(),
                request.parentDepartmentId(),
                null
        );
        validateDepartmentName(request.companyId(), request.parentDepartmentId(), request.departmentName(), null);

        String departmentId = buildId("dept");
        DepartmentTreeMeta treeMeta = buildTreeMeta(departmentId, parent);
        systemDepartmentMapper.insertDepartment(new SystemDepartmentRecord(
                departmentId,
                request.companyId(),
                normalizeParentId(request.parentDepartmentId()),
                treeMeta.rootDepartmentId(),
                treeMeta.treeLevel(),
                treeMeta.treePath(),
                request.departmentName(),
                request.enabled()
        ));
        return new SystemDepartmentMutationResponse(departmentId);
    }

    /**
     * 更新部门。
     */
    @Transactional
    public SystemDepartmentMutationResponse update(String departmentId, SaveSystemDepartmentRequest request) {
        SystemDepartmentDetailResponse current = loadDepartment(departmentId);
        validateCompanyExists(request.companyId());
        SystemDepartmentDetailResponse parent = validateParentDepartment(
                request.companyId(),
                request.parentDepartmentId(),
                current
        );
        validateDepartmentName(request.companyId(), request.parentDepartmentId(), request.departmentName(), departmentId);

        DepartmentTreeMeta treeMeta = buildTreeMeta(departmentId, parent);
        systemDepartmentMapper.updateDepartment(new SystemDepartmentRecord(
                departmentId,
                request.companyId(),
                normalizeParentId(request.parentDepartmentId()),
                treeMeta.rootDepartmentId(),
                treeMeta.treeLevel(),
                treeMeta.treePath(),
                request.departmentName(),
                request.enabled()
        ));

        if (!Objects.equals(current.companyId(), request.companyId())
                || !Objects.equals(current.rootDepartmentId(), treeMeta.rootDepartmentId())
                || !Objects.equals(current.treePath(), treeMeta.treePath())
                || !Objects.equals(current.treeLevel(), treeMeta.treeLevel())) {
            systemDepartmentMapper.updateDepartmentSubtreeTreeMeta(
                    current.treePath(),
                    treeMeta.treePath(),
                    treeMeta.treeLevel() - current.treeLevel(),
                    request.companyId(),
                    treeMeta.rootDepartmentId()
            );
        }

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

    private SystemDepartmentDetailResponse loadDepartment(String departmentId) {
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

    private SystemDepartmentDetailResponse validateParentDepartment(
            String companyId,
            String parentDepartmentId,
            SystemDepartmentDetailResponse currentDepartment
    ) {
        if (parentDepartmentId == null || parentDepartmentId.isBlank()) {
            return null;
        }
        if (currentDepartment != null && parentDepartmentId.equals(currentDepartment.departmentId())) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "父部门不能选择自己",
                    Map.of("parentDepartmentId", parentDepartmentId)
            );
        }

        SystemDepartmentDetailResponse parent = loadDepartment(parentDepartmentId);
        if (!companyId.equals(parent.companyId())) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "父部门必须属于同一公司",
                    Map.of("companyId", companyId, "parentDepartmentId", parentDepartmentId)
            );
        }
        if (currentDepartment != null && parent.treePath() != null && currentDepartment.treePath() != null) {
            String currentTreePathPrefix = currentDepartment.treePath() + "/";
            if (parent.treePath().equals(currentDepartment.treePath()) || parent.treePath().startsWith(currentTreePathPrefix)) {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "父部门不能选择当前部门及其子部门",
                        Map.of("parentDepartmentId", parentDepartmentId)
                );
            }
        }
        return parent;
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
        String rootDepartmentId = null;

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
                case "rootDepartmentId" -> rootDepartmentId = value;
                default -> {
                }
            }
        }
        return new Filters(enabled, companyId, parentDepartmentId, rootDepartmentId);
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
            case "treeLevel" -> "d.tree_level";
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

    private DepartmentTreeMeta buildTreeMeta(String departmentId, SystemDepartmentDetailResponse parent) {
        if (parent == null) {
            return new DepartmentTreeMeta(departmentId, 1, departmentId);
        }
        return new DepartmentTreeMeta(
                parent.rootDepartmentId(),
                parent.treeLevel() + 1,
                parent.treePath() + "/" + departmentId
        );
    }

    private DepartmentTreeMeta resolveTreeMetaForBackfill(
            String departmentId,
            Map<String, SystemDepartmentDetailResponse> departmentMap,
            Map<String, DepartmentTreeMeta> resolvedMeta,
            Set<String> visiting
    ) {
        DepartmentTreeMeta cached = resolvedMeta.get(departmentId);
        if (cached != null) {
            return cached;
        }
        if (!visiting.add(departmentId)) {
            DepartmentTreeMeta cyclicMeta = new DepartmentTreeMeta(departmentId, 1, departmentId);
            resolvedMeta.put(departmentId, cyclicMeta);
            return cyclicMeta;
        }

        SystemDepartmentDetailResponse department = departmentMap.get(departmentId);
        if (department == null) {
            DepartmentTreeMeta orphanMeta = new DepartmentTreeMeta(departmentId, 1, departmentId);
            resolvedMeta.put(departmentId, orphanMeta);
            visiting.remove(departmentId);
            return orphanMeta;
        }

        String parentDepartmentId = normalizeParentId(department.parentDepartmentId());
        DepartmentTreeMeta treeMeta;
        if (parentDepartmentId == null || !departmentMap.containsKey(parentDepartmentId)) {
            treeMeta = new DepartmentTreeMeta(departmentId, 1, departmentId);
        } else {
            DepartmentTreeMeta parentMeta = resolveTreeMetaForBackfill(
                    parentDepartmentId,
                    departmentMap,
                    resolvedMeta,
                    visiting
            );
            SystemDepartmentDetailResponse parent = departmentMap.get(parentDepartmentId);
            if (parent == null || !Objects.equals(parent.companyId(), department.companyId())) {
                treeMeta = new DepartmentTreeMeta(departmentId, 1, departmentId);
            } else {
                treeMeta = new DepartmentTreeMeta(
                        parentMeta.rootDepartmentId(),
                        parentMeta.treeLevel() + 1,
                        parentMeta.treePath() + "/" + departmentId
                );
            }
        }

        resolvedMeta.put(departmentId, treeMeta);
        visiting.remove(departmentId);
        return treeMeta;
    }

    private List<String> splitTreePath(String treePath) {
        if (treePath == null || treePath.isBlank()) {
            return List.of();
        }
        return List.of(treePath.split("/"));
    }

    private List<SystemDepartmentTreeNodeResponse> buildTree(List<SystemDepartmentDetailResponse> flatNodes) {
        if (flatNodes == null || flatNodes.isEmpty()) {
            return List.of();
        }
        Map<String, MutableTreeNode> nodeMap = new LinkedHashMap<>();
        for (SystemDepartmentDetailResponse node : flatNodes) {
            nodeMap.put(node.departmentId(), new MutableTreeNode(node));
        }

        List<MutableTreeNode> roots = new ArrayList<>();
        for (MutableTreeNode node : nodeMap.values()) {
            if (node.parentDepartmentId == null || !nodeMap.containsKey(node.parentDepartmentId)) {
                roots.add(node);
                continue;
            }
            nodeMap.get(node.parentDepartmentId).children.add(node);
        }

        sortNodes(roots);
        return roots.stream().map(MutableTreeNode::toResponse).toList();
    }

    private void sortNodes(List<MutableTreeNode> nodes) {
        nodes.sort(Comparator
                .comparing((MutableTreeNode node) -> node.companyName == null ? "" : node.companyName)
                .thenComparing(node -> node.treePath == null ? "" : node.treePath)
                .thenComparing(node -> node.departmentName == null ? "" : node.departmentName));
        nodes.forEach(node -> sortNodes(node.children));
    }

    public record Filters(
            Boolean enabled,
            String companyId,
            String parentDepartmentId,
            String rootDepartmentId
    ) {
    }

    private record DepartmentTreeMeta(
            String rootDepartmentId,
            int treeLevel,
            String treePath
    ) {
    }

    private static final class MutableTreeNode {
        private final String departmentId;
        private final String companyId;
        private final String companyName;
        private final String parentDepartmentId;
        private final String parentDepartmentName;
        private final String rootDepartmentId;
        private final String rootDepartmentName;
        private final Integer treeLevel;
        private final String treePath;
        private final String departmentName;
        private final boolean enabled;
        private final List<MutableTreeNode> children = new ArrayList<>();

        private MutableTreeNode(SystemDepartmentDetailResponse node) {
            this.departmentId = node.departmentId();
            this.companyId = node.companyId();
            this.companyName = node.companyName();
            this.parentDepartmentId = node.parentDepartmentId();
            this.parentDepartmentName = node.parentDepartmentName();
            this.rootDepartmentId = node.rootDepartmentId();
            this.rootDepartmentName = node.rootDepartmentName();
            this.treeLevel = node.treeLevel();
            this.treePath = node.treePath();
            this.departmentName = node.departmentName();
            this.enabled = node.enabled();
        }

        private SystemDepartmentTreeNodeResponse toResponse() {
            return new SystemDepartmentTreeNodeResponse(
                    departmentId,
                    companyId,
                    companyName,
                    parentDepartmentId,
                    parentDepartmentName,
                    rootDepartmentId,
                    rootDepartmentName,
                    treeLevel,
                    treePath,
                    departmentName,
                    enabled,
                    children.stream().map(MutableTreeNode::toResponse).toList()
            );
        }
    }
}
