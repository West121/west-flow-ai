package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;

/**
 * 项目关联对象写入请求。
 */
public record PlmProjectLinkRequest(
        @NotBlank(message = "linkType 不能为空")
        String linkType,
        String targetBusinessType,
        @NotBlank(message = "targetId 不能为空")
        String targetId,
        String targetNo,
        String targetTitle,
        String targetStatus,
        String targetHref,
        String summary
) {
}
