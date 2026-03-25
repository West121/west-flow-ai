package com.westflow.system.org.department.response;

import java.util.List;

/**
 * 部门树节点响应。
 */
public record SystemDepartmentTreeNodeResponse(
        String departmentId,
        String companyId,
        String companyName,
        String parentDepartmentId,
        String parentDepartmentName,
        String rootDepartmentId,
        String rootDepartmentName,
        Integer treeLevel,
        String treePath,
        String departmentName,
        boolean enabled,
        List<SystemDepartmentTreeNodeResponse> children
) {
}
