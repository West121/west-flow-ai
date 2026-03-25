package com.westflow.system.role.response;

import java.util.List;

/**
 * 角色表单下拉选项响应。
 */
public record SystemRoleFormOptionsResponse(
        List<MenuOption> menus,
        List<ScopeTypeOption> scopeTypes,
        List<CompanyOption> companies,
        List<DepartmentOption> departments,
        List<UserOption> users
) {

    public record MenuOption(
            String id,
            String name,
            String menuType,
            String parentMenuName
    ) {
    }

    public record ScopeTypeOption(
            String code,
            String name
    ) {
    }

    public record CompanyOption(
            String id,
            String name
    ) {
    }

    public record DepartmentOption(
            String id,
            String name,
            String companyId,
            String companyName
    ) {
    }

    public record UserOption(
            String id,
            String name,
            String departmentId,
            String departmentName
    ) {
    }
}
