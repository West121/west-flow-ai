package com.westflow.system.menu.response;

import java.util.List;

/**
 * 菜单树节点响应。
 */
public record SystemMenuTreeNodeResponse(
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
        boolean enabled,
        List<SystemMenuTreeNodeResponse> children
) {
}
