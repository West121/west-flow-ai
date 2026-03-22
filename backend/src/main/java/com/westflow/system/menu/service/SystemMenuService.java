package com.westflow.system.menu.service;

import com.westflow.common.error.ContractException;
import com.westflow.common.query.FilterItem;
import com.westflow.common.query.PageRequest;
import com.westflow.common.query.PageResponse;
import com.westflow.common.query.SortItem;
import com.westflow.system.menu.api.SaveSystemMenuRequest;
import com.westflow.system.menu.api.SystemMenuDetailResponse;
import com.westflow.system.menu.api.SystemMenuFormOptionsResponse;
import com.westflow.system.menu.api.SystemMenuListItemResponse;
import com.westflow.system.menu.api.SystemMenuMutationResponse;
import com.westflow.system.menu.mapper.SystemMenuMapper;
import java.util.List;
import java.util.Map;
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
    private static final List<String> SUPPORTED_MENU_TYPES = List.of("DIRECTORY", "MENU", "BUTTON");

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
                        new SystemMenuFormOptionsResponse.MenuTypeOption("BUTTON", "按钮")
                ),
                systemMenuMapper.selectParentMenuOptions()
        );
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
        if ("BUTTON".equals(parentMenu.menuType())) {
            throw new ContractException(
                    "VALIDATION.REQUEST_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "按钮类型菜单不能作为父菜单",
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
}
