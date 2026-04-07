package com.westflow.plm.api;

/**
 * PLM 提交验证请求。
 */
public record SubmitPlmValidationRequest(
        String validationOwner,
        String validationSummary
) {
}
