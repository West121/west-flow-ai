package com.westflow.workflowadmin.api.response;

import java.util.List;

/**
 * 审批意见配置表单选项。
 */
public record ApprovalOpinionConfigFormOptionsResponse(
        List<ActionTypeOption> actionTypes,
        List<ToolbarActionOption> toolbarActions
) {

    public record ActionTypeOption(String value, String label) {
    }

    public record ToolbarActionOption(String value, String label) {
    }
}
