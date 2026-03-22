package com.westflow.identity.service;

import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.identity.dto.LoginRequest;
import com.westflow.identity.dto.LoginResponse;
import java.util.List;

/**
 * 统一认证与当前用户上下文服务。
 */
public interface IdentityAuthService {

    /**
     * 执行登录并返回令牌信息。
     */
    LoginResponse login(LoginRequest request);

    /**
     * 获取当前登录人的完整上下文。
     */
    CurrentUserResponse currentUser();

    /**
     * 切换当前活跃岗位上下文。
     */
    void switchContext(String activePostId);

    /**
     * 读取用户权限编码集合。
     */
    List<String> permissionsByUserId(String userId);

    /**
     * 读取用户角色编码集合。
     */
    List<String> rolesByUserId(String userId);

    /**
     * 判断两个用户之间是否存在有效代理关系。
     */
    boolean isActiveDelegate(String principalUserId, String delegateUserId);

    /**
     * 判断用户是否为流程管理员。
     */
    boolean isProcessAdmin(String userId);

    /**
     * 判断用户是否为平台管理员。
     */
    boolean isSystemAdmin(String userId);

    /**
     * 判断用户是否可以访问 Phase 2 系统管理模块。
     */
    boolean canAccessPhase2SystemManagement(String userId);

    /**
     * 判断权限码是否属于 Phase 2 系统管理权限。
     */
    boolean isPhase2SystemPermission(String permissionCode);
}
