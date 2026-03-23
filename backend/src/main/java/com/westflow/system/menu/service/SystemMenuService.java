package com.westflow.system.menu.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.system.menu.api.SaveSystemMenuRequest;
import com.westflow.system.menu.api.SidebarMenuFlatNode;
import com.westflow.system.menu.api.SidebarMenuNodeResponse;
import com.westflow.system.menu.api.SystemMenuDetailResponse;
import com.westflow.system.menu.api.SystemMenuFormOptionsResponse;
import com.westflow.system.menu.api.SystemMenuListItemResponse;
import com.westflow.system.menu.api.SystemMenuMutationResponse;
import com.westflow.system.menu.api.SystemMenuTreeFlatNode;
import com.westflow.system.menu.api.SystemMenuTreeNodeResponse;
import com.westflow.system.menu.mapper.SystemMenuMapper;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 系统菜单管理服务。
 */
@Service
@RequiredArgsConstructor
public class SystemMenuService {

    private static final List<String> SUPPORTED_FILTER_FIELDS = List.of("status", "visible", "menuType");
    private static final List<String> SUPPORTED_SORT_FIELDS = List.of("createdAt", "menuName", "sortOrder");
    private static final List<String> SUPPORTED_MENU_TYPES = List.of("DIRECTORY", "MENU", "PERMISSION");

    private final SystemMenuMapper systemMenuMapper;

    /**
     * 分页查询菜单。
     */
    public PageResponse<SystemMenuListItemResponse> page(PageRequest request) {
        // 菜单树支持筛选、排序和关键字搜索，列表层只做基础聚合。
        Boolean enabled = null;
        Boolean visible = null;
        String menuType = null;

        for (FilterItem filter : request.filters()) {
            if (!SUPPORTED_FILTER_FIELDS.contains(filter.field())) {
                throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
            if (!List.of("eq").contains(filter.operator())) {
                throw unsupported("不支持的筛选操作符", filter.operator(), List.of("eq"));
            }

            String value = filter.value() == null ? null : filter.value().asText();
            switch (filter.field()) {
                case "status" -> enabled = resolveBooleanEnum(value, "状态筛选值不合法");
                case "visible" -> visible = resolveBooleanEnum(value, "显示筛选值不合法");
                case "menuType" -> menuType = resolveMenuType(value);
                default -> throw unsupported("不支持的筛选字段", filter.field(), SUPPORTED_FILTER_FIELDS);
            }
        }

        String orderBy = resolveOrderBy(request.sorts());
        String orderDirection = resolveOrderDirection(request.sorts());
        long total = systemMenuMapper.countPage(request.keyword(), enabled, visible, menuType);
        long pageSize = request.pageSize();
        long pages = total == 0 ? 0 : (total + pageSize - 1) / pageSize;
        long offset = (long) (request.page() - 1) * pageSize;

        List<SystemMenuListItemResponse> records = total == 0
                ? List.of()
                : systemMenuMapper.selectPage(
                        request.keyword(),
                        enabled,
                        visible,
                        menuType,
                        orderBy,
                        orderDirection,
                        pageSize,
                        offset
                );

        return new PageResponse<>(request.page(), pageSize, total, pages, records, List.of());
    }

    /**
     * 查询菜单详情。
     */
    public SystemMenuDetailResponse detail(String menuId) {
        // 菜单详情主要用于编辑页和父级校验。
        SystemMenuDetailResponse detail = systemMenuMapper.selectDetail(menuId);
        if (detail == null) {
            throw new ContractException(
                    "BIZ.RESOURCE_NOT_FOUND",
                    HttpStatus.NOT_FOUND,
                    "菜单不存在",
                    Map.of("menuId", menuId)
            );
        }
        return detail;
    }

    /**
     * 获取菜单表单选项。
     */
    public SystemMenuFormOptionsResponse formOptions() {
        return new SystemMenuFormOptionsResponse(
                List.of(
                        new SystemMenuFormOptionsResponse.MenuTypeOption("DIRECTORY", "目录"),
                        new SystemMenuFormOptionsResponse.MenuTypeOption("MENU", "菜单"),
                        new SystemMenuFormOptionsResponse.MenuTypeOption("PERMISSION", "权限")
                ),
                systemMenuMapper.selectParentMenuOptions()
        );
    }

    /**
     * 查询完整菜单树。
     */
    public List<SystemMenuTreeNodeResponse> tree() {
        List<SystemMenuTreeFlatNode> flatNodes = systemMenuMapper.selectTreeNodes();
        return buildTree(flatNodes);
    }

    /**
     * 查询当前用户可见的侧边栏菜单树。
     */
    public List<SidebarMenuNodeResponse> sidebarTree(String userId) {
        Set<String> grantedMenuIds = new HashSet<>(systemMenuMapper.selectGrantedMenuIdsByUserId(userId));
        if (grantedMenuIds.isEmpty()) {
            return List.of();
        }

        List<SidebarMenuFlatNode> flatNodes = systemMenuMapper.selectNavigableSidebarNodes();
        Map<String, SidebarMenuNodeResponse> nodeMap = new HashMap<>();
        for (SidebarMenuFlatNode node : flatNodes) {
            nodeMap.put(node.menuId(), new SidebarMenuNodeResponse(
                    node.menuId(),
                    node.parentMenuId(),
                    node.title(),
                    node.menuType(),
                    node.routePath(),
                    node.iconName(),
                    node.sortOrder(),
                    List.of()
            ));
        }

        Set<String> visibleNodeIds = new HashSet<>();
        grantedMenuIds.stream()
                .map(nodeMap::get)
                .filter(node -> node != null)
                .forEach(node -> visibleNodeIds.add(node.menuId()));

        List<SidebarMenuNodeResponse> filteredNodes = flatNodes.stream()
                .filter(node -> visibleNodeIds.contains(node.menuId()))
                .map(node -> new SidebarMenuNodeResponse(
                        node.menuId(),
                        node.parentMenuId(),
                        node.title(),
                        node.menuType(),
                        node.routePath(),
                        node.iconName(),
                        node.sortOrder(),
                        List.of()
                ))
                .toList();
        return buildSidebarTree(filteredNodes);
    }

    /**
     * 新建菜单。
     */
    @Transactional
    public SystemMenuMutationResponse create(SaveSystemMenuRequest request) {
        String parentMenuId = normalizeNullable(request.parentMenuId());
        String menuType = resolveMenuType(request.menuType());
        validateMenuName(request.menuName(), parentMenuId, null);
        validateParentMenu(parentMenuId, null);
        SystemMenuEntity entity = new SystemMenuEntity(
                buildId("menu"),
                parentMenuId,
                request.menuName().trim(),
                menuType,
                normalizeNullable(request.routePath()),
                normalizeNullable(request.componentPath()),
                normalizeNullable(request.permissionCode()),
                normalizeNullable(request.iconName()),
                request.sortOrder(),
                request.visible(),
                request.enabled()
        );
        systemMenuMapper.insertMenu(entity);
        return new SystemMenuMutationResponse(entity.id());
    }

    /**
     * 更新菜单。
     */
    @Transactional
    public SystemMenuMutationResponse update(String menuId, SaveSystemMenuRequest request) {
        detail(menuId);
        String parentMenuId = normalizeNullable(request.parentMenuId());
        String menuType = resolveMenuType(request.menuType());
        validateMenuName(request.menuName(), parentMenuId, menuId);
        validateParentMenu(parentMenuId, menuId);
        systemMenuMapper.updateMenu(new SystemMenuEntity(
                menuId,
                parentMenuId,
                request.menuName().trim(),
                menuType,
                normalizeNullable(request.routePath()),
                normalizeNullable(request.componentPath()),
                normalizeNullable(request.permissionCode()),
                normalizeNullable(request.iconName()),
                request.sortOrder(),
                request.visible(),
                request.enabled()
        ));
        return new SystemMenuMutationResponse(menuId);
    }

    private void validateMenuName(String menuName, String parentMenuId, String excludeMenuId) {
        Long total = systemMenuMapper.countByMenuNameAndParent(menuName.trim(), parentMenuId, excludeMenuId);
        if (total != null && total > 0) {
            throw new ContractException(
                    "BIZ.MENU_NAME_DUPLICATED",
                    HttpStatus.CONFLICT,
                    "同级菜单名称已存在",
                    Map.of("menuName", menuName, "parentMenuId", parentMenuId)
            );
        }
    }

    private void validateParentMenu(String parentMenuId, String currentMenuId) {
        if (parentMenuId == null) {
            return;
        }
        if (parentMenuId.equals(currentMenuId)) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "父级菜单不能选择自己",
                    Map.of("parentMenuId", parentMenuId)
            );
        }

        SystemMenuDetailResponse parentMenu = detail(parentMenuId);
        if ("PERMISSION".equals(parentMenu.menuType())) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "权限类型菜单不能作为父菜单",
                    Map.of("parentMenuId", parentMenuId)
            );
        }

        if (currentMenuId == null) {
            return;
        }

        // 往上追父级，避免出现循环引用。
        String cursor = parentMenu.parentMenuId();
        while (cursor != null && !cursor.isBlank()) {
            if (currentMenuId.equals(cursor)) {
                throw new ContractException(
                        "VALIDATION.REQUEST_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "父级菜单不能形成循环引用",
                        Map.of("parentMenuId", parentMenuId, "menuId", currentMenuId)
                );
            }
            cursor = systemMenuMapper.selectParentMenuId(cursor);
        }
    }

    private Boolean resolveBooleanEnum(String value, String message) {
        if ("ENABLED".equals(value)) {
            return true;
        }
        if ("DISABLED".equals(value)) {
            return false;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                message,
                Map.of("value", value)
        );
    }

    private String resolveMenuType(String menuType) {
        String normalized = normalizeNullable(menuType);
        if (normalized != null && SUPPORTED_MENU_TYPES.contains(normalized)) {
            return normalized;
        }
        throw new ContractException(
                "VALIDATION.REQUEST_INVALID",
                HttpStatus.BAD_REQUEST,
                "菜单类型不合法",
                Map.of("menuType", menuType, "allowedTypes", SUPPORTED_MENU_TYPES)
        );
    }

    private List<SystemMenuTreeNodeResponse> buildTree(List<SystemMenuTreeFlatNode> flatNodes) {
        Map<String, MutableTreeNode> nodeMap = new HashMap<>();
        for (SystemMenuTreeFlatNode node : flatNodes) {
            nodeMap.put(node.menuId(), new MutableTreeNode(node));
        }

        List<MutableTreeNode> roots = new ArrayList<>();
        for (MutableTreeNode node : nodeMap.values()) {
            if (node.parentMenuId == null || !nodeMap.containsKey(node.parentMenuId)) {
                roots.add(node);
                continue;
            }
            nodeMap.get(node.parentMenuId).children.add(node);
        }

        sortMutableNodes(roots);
        return roots.stream().map(MutableTreeNode::toResponse).toList();
    }

    private List<SidebarMenuNodeResponse> buildSidebarTree(List<SidebarMenuNodeResponse> flatNodes) {
        Map<String, MutableSidebarNode> nodeMap = new HashMap<>();
        for (SidebarMenuNodeResponse node : flatNodes) {
            nodeMap.put(node.menuId(), new MutableSidebarNode(node));
        }

        List<MutableSidebarNode> roots = new ArrayList<>();
        for (MutableSidebarNode node : nodeMap.values()) {
            if (node.parentMenuId == null || !nodeMap.containsKey(node.parentMenuId)) {
                roots.add(node);
                continue;
            }
            nodeMap.get(node.parentMenuId).children.add(node);
        }

        sortSidebarNodes(roots);
        return roots.stream().map(MutableSidebarNode::toResponse).toList();
    }

    private void sortMutableNodes(List<MutableTreeNode> nodes) {
        nodes.sort(Comparator
                .comparing((MutableTreeNode node) -> node.sortOrder == null ? Integer.MAX_VALUE : node.sortOrder)
                .thenComparing(node -> node.menuName == null ? "" : node.menuName));
        nodes.forEach(node -> sortMutableNodes(node.children));
    }

    private void sortSidebarNodes(List<MutableSidebarNode> nodes) {
        nodes.sort(Comparator
                .comparing((MutableSidebarNode node) -> node.sortOrder == null ? Integer.MAX_VALUE : node.sortOrder)
                .thenComparing(node -> node.title == null ? "" : node.title));
        nodes.forEach(node -> sortSidebarNodes(node.children));
    }

    private String resolveOrderBy(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "m.sort_order";
        }
        SortItem sort = sorts.get(0);
        if (!SUPPORTED_SORT_FIELDS.contains(sort.field())) {
            throw unsupported("不支持的排序字段", sort.field(), SUPPORTED_SORT_FIELDS);
        }
        return switch (sort.field()) {
            case "menuName" -> "m.menu_name";
            case "sortOrder" -> "m.sort_order";
            default -> "m.created_at";
        };
    }

    private String resolveOrderDirection(List<SortItem> sorts) {
        if (sorts == null || sorts.isEmpty()) {
            return "ASC";
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

    public record SystemMenuEntity(
            String id,
            String parentMenuId,
            String menuName,
            String menuType,
            String routePath,
            String componentPath,
            String permissionCode,
            String iconName,
            Integer sortOrder,
            Boolean visible,
            Boolean enabled
    ) {
    }

    private static final class MutableTreeNode {
        private final String menuId;
        private final String parentMenuId;
        private final String menuName;
        private final String menuType;
        private final String routePath;
        private final String componentPath;
        private final String permissionCode;
        private final String iconName;
        private final Integer sortOrder;
        private final boolean visible;
        private final boolean enabled;
        private final List<MutableTreeNode> children = new ArrayList<>();

        private MutableTreeNode(SystemMenuTreeFlatNode node) {
            this.menuId = node.menuId();
            this.parentMenuId = node.parentMenuId();
            this.menuName = node.menuName();
            this.menuType = node.menuType();
            this.routePath = node.routePath();
            this.componentPath = node.componentPath();
            this.permissionCode = node.permissionCode();
            this.iconName = node.iconName();
            this.sortOrder = node.sortOrder();
            this.visible = node.visible();
            this.enabled = node.enabled();
        }

        private SystemMenuTreeNodeResponse toResponse() {
            return new SystemMenuTreeNodeResponse(
                    menuId,
                    parentMenuId,
                    menuName,
                    menuType,
                    routePath,
                    componentPath,
                    permissionCode,
                    iconName,
                    sortOrder,
                    visible,
                    enabled,
                    children.stream().map(MutableTreeNode::toResponse).toList()
            );
        }
    }

    private static final class MutableSidebarNode {
        private final String menuId;
        private final String parentMenuId;
        private final String title;
        private final String menuType;
        private final String routePath;
        private final String iconName;
        private final Integer sortOrder;
        private final List<MutableSidebarNode> children = new ArrayList<>();

        private MutableSidebarNode(SidebarMenuNodeResponse node) {
            this.menuId = node.menuId();
            this.parentMenuId = node.parentMenuId();
            this.title = node.title();
            this.menuType = node.menuType();
            this.routePath = node.routePath();
            this.iconName = node.iconName();
            this.sortOrder = node.sortOrder();
        }

        private SidebarMenuNodeResponse toResponse() {
            return new SidebarMenuNodeResponse(
                    menuId,
                    parentMenuId,
                    title,
                    menuType,
                    routePath,
                    iconName,
                    sortOrder,
                    children.stream().map(MutableSidebarNode::toResponse).toList()
            );
        }
    }
}
