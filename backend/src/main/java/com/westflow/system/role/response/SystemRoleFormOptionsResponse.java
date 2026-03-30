package com.westflow.system.role.response;

import java.util.List;

/**
 * 角色表单下拉选项响应。
 */
public record SystemRoleFormOptionsResponse(
        // 菜单选项列表。
        List<MenuOption> menus,
        // 数据范围类型选项。
        List<ScopeTypeOption> scopeTypes,
        // 公司选项列表。
        List<CompanyOption> companies,
        // 部门选项列表。
        List<DepartmentOption> departments,
        // 用户选项列表。
        List<UserOption> users
) {

    public record MenuOption(
            // 菜单主键。
            String id,
            // 菜单名称。
            String name,
            // 菜单类型。
            String menuType,
            // 上级菜单名称。
            String parentMenuName
    ) {
    }

    public record ScopeTypeOption(
            // 数据范围类型编码。
            String code,
            // 数据范围类型名称。
            String name
    ) {
    }

    public record CompanyOption(
            // 公司主键。
            String id,
            // 公司名称。
            String name
    ) {
    }

    public record DepartmentOption(
            // 部门主键。
            String id,
            // 部门名称。
            String name,
            // 所属公司主键。
            String companyId,
            // 所属公司名称。
            String companyName
    ) {
    }

    public record UserOption(
            // 用户主键。
            String id,
            // 用户名称。
            String name,
            // 所属部门主键。
            String departmentId,
            // 所属部门名称。
            String departmentName
    ) {
    }
}
