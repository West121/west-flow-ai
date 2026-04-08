package com.westflow.plm.api;

import java.util.List;

/**
 * PLM 当前用户权限聚合响应。
 */
public record PlmPermissionSummaryResponse(
        String businessType,
        String billId,
        String currentUserId,
        List<String> currentRoleCodes,
        List<String> matchedRoleCodes,
        List<String> domainCodes,
        List<String> grantedPermissionCodes,
        boolean canReadBill,
        boolean canManageBill,
        boolean canReadObjects,
        boolean canChangeObjects,
        boolean canReadBaselines,
        boolean canOperateImplementation,
        boolean canAdminAcl
) {
}
