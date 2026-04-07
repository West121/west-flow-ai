package com.westflow.plm.api;

/**
 * PLM 开始实施请求。
 */
public record StartPlmImplementationRequest(
        String implementationOwner,
        String implementationSummary
) {
}
