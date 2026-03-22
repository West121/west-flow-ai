package com.westflow.system.org.post.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveSystemPostRequest(
        @NotBlank(message = "请选择所属部门")
        String departmentId,
        @NotBlank(message = "请输入岗位名称")
        String postName,
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {
}
