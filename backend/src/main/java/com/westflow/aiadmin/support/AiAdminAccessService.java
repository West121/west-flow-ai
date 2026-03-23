package com.westflow.aiadmin.support;

import com.westflow.common.error.ContractException;
import com.westflow.identity.service.IdentityAuthService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * AI 管理后台访问权限校验。
 */
@Service
@RequiredArgsConstructor
public class AiAdminAccessService {

    private final IdentityAuthService identityAuthService;

    /**
     * 校验当前用户是否可以访问 AI 管理后台。
     */
    public void ensureAiAdminAccess() {
        String userId = currentUserId();
        if (!identityAuthService.isSystemAdmin(userId) && !identityAuthService.isProcessAdmin(userId)) {
            throw new ContractException(
                    "AUTH.FORBIDDEN",
                    HttpStatus.FORBIDDEN,
                    "仅系统管理员或流程管理员可以访问 AI 管理后台",
                    Map.of("userId", userId)
            );
        }
    }

    /**
     * 读取当前登录用户 ID。
     */
    public String currentUserId() {
        return identityAuthService.currentUser().userId();
    }
}
