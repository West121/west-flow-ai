package com.westflow.system.org.department.response;

/**
 * 部门详情响应。
 */
public record SystemDepartmentDetailResponse(
        // 部门主键。
        String departmentId,
        // 所属公司主键。
        String companyId,
        // 所属公司名称。
        String companyName,
        // 上级部门主键。
        String parentDepartmentId,
        // 上级部门名称。
        String parentDepartmentName,
        // 根部门主键。
        String rootDepartmentId,
        // 根部门名称。
        String rootDepartmentName,
        // 树层级。
        Integer treeLevel,
        // 树路径。
        String treePath,
        // 部门名称。
        String departmentName,
        // 是否启用。
        boolean enabled
) {
}
