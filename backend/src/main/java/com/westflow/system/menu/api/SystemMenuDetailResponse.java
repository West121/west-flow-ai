package com.westflow.system.menu.api;

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
