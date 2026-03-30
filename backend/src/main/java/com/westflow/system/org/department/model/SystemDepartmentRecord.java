package com.westflow.system.org.department.model;

/**
 * 部门表实体。
 */
public record SystemDepartmentRecord(
        // 部门主键。
        String id,
        // 所属公司主键。
        String companyId,
        // 上级部门主键。
        String parentDepartmentId,
        // 根部门主键。
        String rootDepartmentId,
        // 树层级。
        Integer treeLevel,
        // 树路径。
        String treePath,
        // 部门名称。
        String departmentName,
        // 是否启用。
        Boolean enabled
) {
}
