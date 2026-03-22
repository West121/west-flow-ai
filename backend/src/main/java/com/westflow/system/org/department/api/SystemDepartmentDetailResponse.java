package com.westflow.system.org.department.api;

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
