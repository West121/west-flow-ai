package com.westflow.system.menu.response;

import java.time.OffsetDateTime;

/**
 * 菜单列表项响应。
 */
public record SystemMenuListItemResponse(
        String menuId,
        String parentMenuName,
        String menuName,
        String menuType,
        String routePath,
        String permissionCode,
        Integer sortOrder,
        boolean visible,
        String status,
        OffsetDateTime createdAt
) {
}
