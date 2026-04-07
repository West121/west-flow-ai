package com.westflow.processruntime.api.response;

import java.time.Instant;

/**
 * 小程序流程回顾短期访问票据。
 */
public record ProcessReviewTicketResponse(
    /**
     * 短期票据字符串。
     */
    String ticket,
    /**
     * 票据过期时间。
     */
    Instant expiresAt
) {
}
