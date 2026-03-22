package com.westflow.identity.dto;

/**
 * 登录响应。
 */
public record LoginResponse(
        String accessToken,
        String tokenType,
        long expiresIn
) {
}
