package com.westflow.identity.dto;

import jakarta.validation.constraints.NotBlank;

public record SwitchContextRequest(
        @NotBlank(message = "activePostId 不能为空")
        String activePostId
) {
}
