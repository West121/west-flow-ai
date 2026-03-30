package com.westflow.system.menu.model;

/**
 * 菜单表实体。
 */
public record SystemMenuRecord(
        // 菜单标识。
        String id,
        // 上级菜单标识。
        String parentMenuId,
        // 菜单名称。
        String menuName,
        // 菜单类型。
        String menuType,
        // 路由路径。
        String routePath,
        // 前端组件路径。
        String componentPath,
        // 权限编码。
        String permissionCode,
        // 图标名称。
        String iconName,
        // 排序值。
        Integer sortOrder,
        // 是否可见。
        Boolean visible,
        // 是否启用。
        Boolean enabled
) {
}
