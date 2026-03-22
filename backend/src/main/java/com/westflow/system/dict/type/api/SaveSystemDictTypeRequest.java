package com.westflow.system.dict.type.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 字典类型保存请求。
 */
public record SaveSystemDictTypeRequest(
        @NotBlank(message = "字典类型编码不能为空")
        String typeCode,
        @NotBlank(message = "字典类型名称不能为空")
        String typeName,
        String description,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
