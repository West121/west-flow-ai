package com.westflow.system.org.company.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record SaveSystemCompanyRequest(
        @NotBlank(message = "请输入公司名称")
        String companyName,
        @NotNull(message = "请选择启用状态")
        Boolean enabled
) {
}
