package com.westflow.common.api;

public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId
) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data, RequestContext.getOrCreateRequestId());
    }
}
