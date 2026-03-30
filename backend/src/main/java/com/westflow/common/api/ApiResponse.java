package com.westflow.common.api;

/**
 * 统一的接口成功响应体。
 */
public record ApiResponse<T>(
        String code,
        String message,
        T data,
        String requestId
) {

    /**
     * 构造默认成功响应。
     *
     * @param data 响应数据
     * @param <T> 响应体类型
     * @return 成功响应
     */
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>("OK", "success", data, RequestContext.getOrCreateRequestId());
    }
}
