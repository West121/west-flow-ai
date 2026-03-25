package com.westflow.system.org.company.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 公司保存请求，供新建和编辑复用。
 */
public record SaveSystemCompanyRequest(
        @NotBlank(message = "请输入公司名称")
        String companyName,
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {
}
