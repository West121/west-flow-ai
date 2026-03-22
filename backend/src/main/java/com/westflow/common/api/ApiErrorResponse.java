package com.westflow.common.api;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

public record ApiErrorResponse(
        String code,
        String message,
        String requestId,
        String path,
        String timestamp,
        Map<String, Object> details,
        List<FieldErrorItem> fieldErrors
) {

    public static ApiErrorResponse of(
            String code,
            String message,
            Map<String, Object> details,
            List<FieldErrorItem> fieldErrors
    ) {
        return new ApiErrorResponse(
                code,
                message,
                RequestContext.getOrCreateRequestId(),
                RequestContext.currentPath(),
                OffsetDateTime.now(ZoneId.of("Asia/Shanghai")).toString(),
                details,
                fieldErrors
        );
    }

    public record FieldErrorItem(
            String field,
            String code,
            String message
    ) {
    }
}
