package com.westflow.system.org.department.response;

import java.time.OffsetDateTime;

/**
 * 部门列表项响应。
 */
public record SystemDepartmentListItemResponse(
        String departmentId,
        String companyName,
        String parentDepartmentName,
        String departmentName,
        String status,
        OffsetDateTime createdAt
) {
}
