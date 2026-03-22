package com.westflow.system.menu.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 菜单保存请求，供新建和编辑复用。
 */
public record SaveSystemMenuRequest(
        String parentMenuId,
        @NotBlank(message = "请输入菜单名称")
        String menuName,
        @NotBlank(message = "请选择菜单类型")
        String menuType,
        String routePath,
        String componentPath,
        String permissionCode,
        String iconName,
        @NotNull(message = "请输入排序值")
        @PositiveOrZero(message = "排序值不能小于 0")
        Integer sortOrder,
        @NotNull(message = "请选择是否显示")
        Boolean visible,
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {
}
