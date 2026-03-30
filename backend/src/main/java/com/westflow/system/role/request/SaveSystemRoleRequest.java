package com.westflow.system.role.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 角色保存请求，供新建和编辑复用。
 */
public record SaveSystemRoleRequest(
        // 角色名称。
        @NotBlank(message = "请输入角色名称")
        String roleName,
        // 角色编码。
        @NotBlank(message = "请输入角色编码")
        String roleCode,
        // 角色分类。
        @NotBlank(message = "请选择角色分类")
        String roleCategory,
        // 角色说明。
        String description,
        // 菜单主键列表。
        @NotEmpty(message = "请至少选择一个菜单权限")
        List<String> menuIds,
        // 数据权限范围配置。
        @Valid
        List<RoleDataScopeInput> dataScopes,
        // 是否启用。
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {

    public record RoleDataScopeInput(
            // 数据范围类型。
            @NotBlank(message = "请选择数据权限类型")
            String scopeType,
            // 数据范围值。
            @NotBlank(message = "请选择数据权限范围")
            String scopeValue
    ) {
    }
}
