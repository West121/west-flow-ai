package com.westflow.system.user.api;

import java.util.List;

/**
 * 用户表单下拉选项响应。
 */
public record SystemUserFormOptionsResponse(
        List<CompanyOption> companies,
        List<PostOption> posts,
        List<RoleOption> roles
) {

    public record CompanyOption(
            String id,
            String name
    ) {
    }

    public record PostOption(
            String id,
            String name,
            String departmentId,
            String departmentName
    ) {
    }

    public record RoleOption(
            String id,
            String name,
            String roleCode,
            String roleCategory
    ) {
    }
}
