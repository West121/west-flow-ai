package com.westflow.system.org.post.api;

import java.util.List;

/**
 * 岗位表单下拉选项响应。
 */
public record SystemPostFormOptionsResponse(
        List<DepartmentOption> departments
) {

    public record DepartmentOption(
            String id,
            String name,
            String companyId,
            String companyName,
            boolean enabled
    ) {
    }
}
