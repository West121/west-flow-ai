package com.westflow.system.org.department.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveSystemDepartmentRequest(
        @NotBlank(message = "请选择所属公司")
        String companyId,
        String parentDepartmentId,
        @NotBlank(message = "请输入部门名称")
        String departmentName,
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {
}
