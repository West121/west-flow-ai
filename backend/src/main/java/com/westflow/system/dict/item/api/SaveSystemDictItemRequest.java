package com.westflow.system.dict.item.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * 字典项保存请求。
 */
public record SaveSystemDictItemRequest(
        // 所属字典类型标识。
        @NotBlank(message = "所属字典类型不能为空")
        String dictTypeId,
        // 字典项编码。
        @NotBlank(message = "字典项编码不能为空")
        String itemCode,
        // 字典项名称。
        @NotBlank(message = "字典项名称不能为空")
        String itemLabel,
        // 字典项值。
        @NotBlank(message = "字典项值不能为空")
        String itemValue,
        // 排序值。
        @NotNull(message = "排序值不能为空")
        Integer sortOrder,
        // 备注说明。
        String remark,
        // 是否启用。
        @NotNull(message = "启用状态不能为空")
        Boolean enabled
) {
}
