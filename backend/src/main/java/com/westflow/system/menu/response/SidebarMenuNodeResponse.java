package com.westflow.system.menu.response;

import java.util.List;

/**
 * 当前用户侧边栏菜单树节点。
 */
public record SidebarMenuNodeResponse(
        String menuId,
        String parentMenuId,
        String title,
        String menuType,
        String routePath,
        String iconName,
        Integer sortOrder,
        List<SidebarMenuNodeResponse> children
) {
}
