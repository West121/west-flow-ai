package com.westflow.workflowadmin.api.request;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * 保存审批意见配置请求。
 */
public record SaveApprovalOpinionConfigRequest(
        @NotBlank(message = "configCode 不能为空")
        String configCode,
        @NotBlank(message = "configName 不能为空")
        String configName,
        boolean enabled,
        List<String> quickOpinions,
        List<String> toolbarActions,
        List<ButtonStrategy> buttonStrategies,
        String remark
) {

    public record ButtonStrategy(
            @NotBlank(message = "actionType 不能为空")
            String actionType,
            boolean requireOpinion
    ) {
    }
}
