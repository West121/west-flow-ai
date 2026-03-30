package com.westflow.system.org.post.response;

import java.util.List;

/**
 * 岗位表单下拉选项响应。
 */
public record SystemPostFormOptionsResponse(
        // 部门选项列表。
        List<DepartmentOption> departments
) {

    public record DepartmentOption(
            // 部门主键。
            String id,
            // 部门名称。
            String name,
            // 公司主键。
            String companyId,
            // 公司名称。
            String companyName,
            // 是否启用。
            boolean enabled
    ) {
    }
}
