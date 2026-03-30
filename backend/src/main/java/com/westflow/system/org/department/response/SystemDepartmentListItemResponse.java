package com.westflow.system.org.department.response;

import java.time.OffsetDateTime;

/**
 * 部门列表项响应。
 */
public record SystemDepartmentListItemResponse(
        // 部门主键。
        String departmentId,
        // 公司名称。
        String companyName,
        // 上级部门主键。
        String parentDepartmentId,
        // 上级部门名称。
        String parentDepartmentName,
        // 根部门主键。
        String rootDepartmentId,
        // 树层级。
        Integer treeLevel,
        // 树路径。
        String treePath,
        // 部门名称。
        String departmentName,
        // 启用状态。
        String status,
        // 创建时间。
        OffsetDateTime createdAt
) {
}
