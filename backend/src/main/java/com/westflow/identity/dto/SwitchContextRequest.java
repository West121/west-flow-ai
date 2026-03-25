package com.westflow.identity.request;

import jakarta.validation.constraints.NotBlank;

/**
 * 切换岗位上下文请求。
 */
public record SwitchContextRequest(
        @NotBlank(message = "activePostId 不能为空")
        String activePostId
) {
}
