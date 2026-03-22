package com.westflow.system.menu.api;

import java.time.OffsetDateTime;

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
