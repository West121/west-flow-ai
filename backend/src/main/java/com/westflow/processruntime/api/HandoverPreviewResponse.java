package com.westflow.processruntime.api;

import java.util.List;

// 离职转办预览结果，带上来源人与目标人名称。
public record HandoverPreviewResponse(
        String sourceUserId,
        String sourceDisplayName,
        String targetUserId,
        String targetDisplayName,
        int previewTaskCount,
        List<HandoverPreviewTaskItemResponse> previewTasks
) {
}
