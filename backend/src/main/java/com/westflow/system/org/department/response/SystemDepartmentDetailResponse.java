package com.westflow.system.org.department.response;

/**
 * 部门详情响应。
 */
public record SystemDepartmentDetailResponse(
        String departmentId,
        String companyId,
        String companyName,
        String parentDepartmentId,
        String parentDepartmentName,
        String departmentName,
        boolean enabled
) {
}
