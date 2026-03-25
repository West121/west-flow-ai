package com.westflow.system.menu.response;

/**
 * 菜单树构建用的扁平节点。
 */
public record SystemMenuTreeFlatNode(
        String menuId,
        String parentMenuId,
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
