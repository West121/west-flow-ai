package com.westflow.system.role.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record SaveSystemRoleRequest(
        @NotBlank(message = "请输入角色名称")
        String roleName,
        @NotBlank(message = "请输入角色编码")
        String roleCode,
        @NotBlank(message = "请选择角色分类")
        String roleCategory,
        String description,
        @NotEmpty(message = "请至少选择一个菜单权限")
        List<String> menuIds,
        @Valid
        List<RoleDataScopeInput> dataScopes,
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {

    public record RoleDataScopeInput(
            @NotBlank(message = "请选择数据权限类型")
            String scopeType,
            @NotBlank(message = "请选择数据权限范围")
            String scopeValue
    ) {
    }
}
