package com.westflow.processruntime.api.response;

import java.util.List;

// 离职转办预览结果，带上来源人与目标人名称。
public record HandoverPreviewResponse(
        // 来源用户标识
        String sourceUserId,
        // 来源用户名称
        String sourceDisplayName,
        // 目标用户标识
        String targetUserId,
        // 目标展示名称。
        String targetDisplayName,
        // preview任务数量。
        int previewTaskCount,
        // 预览任务列表
        List<HandoverPreviewTaskItemResponse> previewTasks
) {
}
