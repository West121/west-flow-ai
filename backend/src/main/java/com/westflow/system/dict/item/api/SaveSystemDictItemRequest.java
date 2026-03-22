package com.westflow.system.dict.item.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 字典项保存请求。
 */
public record SaveSystemDictItemRequest(
        @NotBlank(message = "所属字典类型不能为空")
        String dictTypeId,
        @NotBlank(message = "字典项编码不能为空")
        String itemCode,
        @NotBlank(message = "字典项名称不能为空")
        String itemLabel,
        @NotBlank(message = "字典项值不能为空")
        String itemValue,
        @NotNull(message = "排序值不能为空")
        Integer sortOrder,
        String remark,
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
