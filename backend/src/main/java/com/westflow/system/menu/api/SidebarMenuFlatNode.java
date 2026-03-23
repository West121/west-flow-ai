package com.westflow.system.menu.api;

/**
 * 侧边栏菜单树构建用的扁平节点。
 */
public record SidebarMenuFlatNode(
        String menuId,
        String parentMenuId,
        String title,
        String menuType,
        String routePath,
        String iconName,
        Integer sortOrder
) {
}
