package com.westflow.system.org.department.api;

import java.util.List;

/**
 * 部门表单下拉选项响应。
 */
public record SystemDepartmentFormOptionsResponse(
        List<CompanyOption> companies,
        List<ParentDepartmentOption> parentDepartments
) {

    public record CompanyOption(
            String id,
            String name,
            boolean enabled
    ) {
    }

    public record ParentDepartmentOption(
            String id,
            String name,
            String companyId,
            String companyName,
            String parentDepartmentId,
            boolean enabled
    ) {
    }
}
