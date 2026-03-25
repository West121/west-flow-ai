package com.westflow.system.org.department.model;

/**
 * 部门表实体。
 */
public record SystemDepartmentRecord(
        String id,
        String companyId,
        String parentDepartmentId,
        String departmentName,
        Boolean enabled
) {
}
