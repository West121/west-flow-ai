package com.westflow.system.menu.api;

/**
 * 菜单详情响应。
 */
public record SystemMenuDetailResponse(
        String menuId,
        String parentMenuId,
        String parentMenuName,
        String menuName,
        String menuType,
        String routePath,
        String componentPath,
        String permissionCode,
        String iconName,
        Integer sortOrder,
        boolean visible,
        boolean enabled
) {
}
