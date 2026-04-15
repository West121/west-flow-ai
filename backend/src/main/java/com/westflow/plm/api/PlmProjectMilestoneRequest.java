package com.westflow.plm.api;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * 项目里程碑写入请求。
 */
public record PlmProjectMilestoneRequest(
        @NotBlank(message = "milestoneCode 不能为空")
        String milestoneCode,
        @NotBlank(message = "milestoneName 不能为空")
        String milestoneName,
        @NotBlank(message = "status 不能为空")
        String status,
        String ownerUserId,
        LocalDateTime plannedAt,
        LocalDateTime actualAt,
        String summary
) {
}
