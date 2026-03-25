package com.westflow.system.menu.model;

/**
 * 菜单表实体。
 */
public record SystemMenuRecord(
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
