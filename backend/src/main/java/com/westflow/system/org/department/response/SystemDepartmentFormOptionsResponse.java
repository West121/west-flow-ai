package com.westflow.system.org.department.response;

import java.util.List;

/**
 * 部门表单下拉选项响应。
 */
public record SystemDepartmentFormOptionsResponse(
        // 公司选项列表。
        List<CompanyOption> companies,
        // 可选上级部门列表。
        List<ParentDepartmentOption> parentDepartments
) {

    public record CompanyOption(
            // 公司主键。
            String id,
            // 公司名称。
            String name,
            // 是否启用。
            boolean enabled
    ) {
    }

    public record ParentDepartmentOption(
            // 部门主键。
            String id,
            // 部门名称。
            String name,
            // 公司主键。
            String companyId,
            // 公司名称。
            String companyName,
            // 上级部门主键。
            String parentDepartmentId,
            // 根部门主键。
            String rootDepartmentId,
            // 树层级。
            Integer treeLevel,
            // 树路径。
            String treePath,
            // 是否启用。
            boolean enabled
    ) {
    }
}
