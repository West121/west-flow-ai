package com.westflow.system.org.post.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 岗位保存请求，供新建和编辑复用。
 */
public record SaveSystemPostRequest(
        // 所属部门主键。
        @NotBlank(message = "请选择所属部门")
        String departmentId,
        // 岗位名称。
        @NotBlank(message = "请输入岗位名称")
        String postName,
        // 是否启用。
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {
}
