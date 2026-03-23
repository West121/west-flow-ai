package com.westflow.system.menu.api;

import java.util.List;

/**
 * 菜单表单下拉选项响应。
 */
public record SystemMenuFormOptionsResponse(
        List<MenuTypeOption> menuTypes,
        List<ParentMenuOption> parentMenus
) {

    public record MenuTypeOption(
            String code,
            String name
    ) {
    }

    public record ParentMenuOption(
            String id,
            String name,
            String menuType,
            boolean enabled
    ) {
    }

    /**
     * 树形菜单选项。
     */
    public record ParentMenuTreeOption(
            String id,
            String parentMenuId,
            String name,
            String menuType,
            boolean enabled
    ) {
    }
}
