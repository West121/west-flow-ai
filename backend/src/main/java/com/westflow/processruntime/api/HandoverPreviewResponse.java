package com.westflow.processruntime.api;

import java.util.List;

// 预览结果带上来源人与目标人名称，系统管理页无需额外查人名。
public record HandoverPreviewResponse(
        String sourceUserId,
        String sourceDisplayName,
        String targetUserId,
        String targetDisplayName,
        int previewTaskCount,
        List<HandoverPreviewTaskItemResponse> previewTasks
) {
}
