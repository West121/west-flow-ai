package com.westflow.identity.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.identity.dto.LoginRequest;
import com.westflow.identity.dto.LoginResponse;
import com.westflow.identity.mapper.AuthUserMapper;
import com.westflow.identity.mapper.AuthUserMapper.AuthUserRecord;
import com.westflow.identity.mapper.AuthUserMapper.UserPostContextRecord;
import com.westflow.identity.mapper.IdentityAccessMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 基于组织库与权限表的真实认证服务。
 */
@Service
public class DatabaseAuthService implements IdentityAuthService {

    private static final String ACTIVE_POST_ID = "activePostId";
    private static final String ROLE_SYSTEM_ADMIN = "SYSTEM_ADMIN";
    private static final String ROLE_PROCESS_ADMIN = "PROCESS_ADMIN";
    private static final int LOGIN_LOCK_THRESHOLD = 5;
    private static final int LOGIN_LOCK_MINUTES = 15;
    private static final List<String> PHASE_2_SYSTEM_PERMISSION_PREFIXES = List.of(
            "system:dict:",
            "system:log:",
            "system:monitor:",
            "system:file:",
            "system:notification:",
            "system:message:"
    );

    private final AuthUserMapper authUserMapper;
    private final IdentityAccessMapper identityAccessMapper;
    private final PasswordService passwordService;

    public DatabaseAuthService(
            AuthUserMapper authUserMapper,
            IdentityAccessMapper identityAccessMapper,
            PasswordService passwordService
    ) {
        this.authUserMapper = authUserMapper;
        this.identityAccessMapper = identityAccessMapper;
        this.passwordService = passwordService;
    }

    /**
     * 执行数据库登录并创建 Sa-Token 会话。
     */
    @Override
    public LoginResponse login(LoginRequest request) {
        AuthUserRecord user = authUserMapper.selectByUsername(request.username());
        if (user == null) {
            throw new ContractException("AUTH.UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }
        ensureLoginAllowed(user);
        if (!passwordService.matches(request.password(), user.passwordHash())) {
            onPasswordMismatch(user);
        }

        authUserMapper.markLoginSuccess(user.userId());
        StpUtil.login(user.userId());
        StpUtil.getTokenSession().set(ACTIVE_POST_ID, user.activePostId());
        return new LoginResponse(StpUtil.getTokenValue(), "Bearer", StpUtil.getTokenTimeout());
    }

    /**
     * 聚合当前登录人的完整上下文。
     */
    @Override
    public CurrentUserResponse currentUser() {
        AuthUserRecord user = requireLoginUser(StpUtil.getLoginIdAsString());
        String activePostId = resolveActivePostId(user);
        UserPostContextRecord activePost = requireOwnedPost(user.userId(), activePostId);
        List<CurrentUserResponse.Delegation> delegations = identityAccessMapper.selectDelegationsByDelegateUserId(user.userId()).stream()
                .filter(delegation -> "ACTIVE".equalsIgnoreCase(delegation.status()))
                .toList();
        return new CurrentUserResponse(
                user.userId(),
                user.username(),
                user.displayName(),
                user.mobile(),
                user.email(),
                user.avatar(),
                user.companyId(),
                activePost.postId(),
                activePost.departmentId(),
                rolesByUserId(user.userId()),
                permissionsByUserId(user.userId()),
                identityAccessMapper.selectDataScopesByUserId(user.userId()),
                authUserMapper.selectPartTimePostsByUserId(user.userId()),
                delegations,
                authUserMapper.selectAiCapabilitiesByUserId(user.userId()),
                identityAccessMapper.selectMenusByUserId(user.userId())
        );
    }

    /**
     * 切换当前活跃岗位上下文。
     */
    @Override
    public void switchContext(String activePostId) {
        String userId = StpUtil.getLoginIdAsString();
        requireOwnedPost(userId, activePostId);
        StpUtil.getTokenSession().set(ACTIVE_POST_ID, activePostId);
    }

    /**
     * 查询用户权限集合。
     */
    @Override
    public List<String> permissionsByUserId(String userId) {
        requireLoginUser(userId);
        return identityAccessMapper.selectPermissionsByUserId(userId);
    }

    /**
     * 查询用户角色集合。
     */
    @Override
    public List<String> rolesByUserId(String userId) {
        requireLoginUser(userId);
        return identityAccessMapper.selectRoleCodesByUserId(userId);
    }

    /**
     * 判断是否存在有效代理关系。
     */
    @Override
    public boolean isActiveDelegate(String principalUserId, String delegateUserId) {
        requireLoginUser(principalUserId);
        requireLoginUser(delegateUserId);
        return identityAccessMapper.selectDelegationsByDelegateUserId(delegateUserId).stream()
                .anyMatch(delegation ->
                        "ACTIVE".equalsIgnoreCase(delegation.status())
                                && principalUserId.equals(delegation.principalUserId())
                                && delegateUserId.equals(delegation.delegateUserId())
                );
    }

    /**
     * 判断是否为流程管理员。
     */
    @Override
    public boolean isProcessAdmin(String userId) {
        return hasRole(userId, ROLE_PROCESS_ADMIN);
    }

    /**
     * 判断是否为平台管理员。
     */
    @Override
    public boolean isSystemAdmin(String userId) {
        return hasRole(userId, ROLE_SYSTEM_ADMIN);
    }

    /**
     * 判断是否可访问系统管理后台。
     */
    @Override
    public boolean canAccessPhase2SystemManagement(String userId) {
        return hasAnyRole(userId, ROLE_SYSTEM_ADMIN, ROLE_PROCESS_ADMIN);
    }

    /**
     * 判断权限码是否属于系统管理权限池。
     */
    @Override
    public boolean isPhase2SystemPermission(String permissionCode) {
        if (permissionCode == null || permissionCode.isBlank()) {
            return false;
        }
        return PHASE_2_SYSTEM_PERMISSION_PREFIXES.stream()
                .anyMatch(permissionCode::startsWith);
    }

    private void ensureLoginAllowed(AuthUserRecord user) {
        if (!Boolean.TRUE.equals(user.enabled()) || !Boolean.TRUE.equals(user.loginEnabled())) {
            throw new ContractException("AUTH.USER_DISABLED", HttpStatus.FORBIDDEN, "账号已停用");
        }
        if (user.lockedUntil() != null && user.lockedUntil().isAfter(OffsetDateTime.now())) {
            throw new ContractException(
                    "AUTH.USER_LOCKED",
                    HttpStatus.LOCKED,
                    "账号已锁定，请稍后重试",
                    Map.of("lockedUntil", user.lockedUntil().toString())
            );
        }
    }

    private void onPasswordMismatch(AuthUserRecord user) {
        int nextFailedCount = (user.failedLoginCount() == null ? 0 : user.failedLoginCount()) + 1;
        OffsetDateTime lockedUntil = nextFailedCount >= LOGIN_LOCK_THRESHOLD
                ? OffsetDateTime.now().plusMinutes(LOGIN_LOCK_MINUTES)
                : null;
        authUserMapper.updateLoginFailureState(user.userId(), nextFailedCount, lockedUntil);
        if (lockedUntil != null) {
            throw new ContractException(
                    "AUTH.USER_LOCKED",
                    HttpStatus.LOCKED,
                    "账号已锁定，请稍后重试",
                    Map.of("lockedUntil", lockedUntil.toString())
            );
        }
        throw new ContractException("AUTH.UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "用户名或密码错误");
    }

    private AuthUserRecord requireLoginUser(String userId) {
        AuthUserRecord user = authUserMapper.selectByUserId(userId);
        if (user == null) {
            throw new ContractException("AUTH.UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "登录用户不存在");
        }
        if (!Boolean.TRUE.equals(user.enabled()) || !Boolean.TRUE.equals(user.loginEnabled())) {
            throw new ContractException("AUTH.USER_DISABLED", HttpStatus.FORBIDDEN, "账号已停用");
        }
        return user;
    }

    private String resolveActivePostId(AuthUserRecord user) {
        String activePostId = StpUtil.getTokenSession().getString(ACTIVE_POST_ID);
        if (activePostId == null || activePostId.isBlank()) {
            activePostId = user.activePostId();
        }
        return activePostId;
    }

    private UserPostContextRecord requireOwnedPost(String userId, String postId) {
        return authUserMapper.selectPostContextsByUserId(userId).stream()
                .filter(post -> post.postId().equals(postId))
                .findFirst()
                .orElseThrow(() -> new ContractException(
                        "VALIDATION.FIELD_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "岗位上下文不存在",
                        Map.of("activePostId", postId)
                ));
    }

    private boolean hasRole(String userId, String roleCode) {
        return hasAnyRole(userId, roleCode);
    }

    private boolean hasAnyRole(String userId, String... roleCodes) {
        List<String> roles = rolesByUserId(userId);
        for (String roleCode : roleCodes) {
            if (roles.stream().anyMatch(role -> role.equalsIgnoreCase(roleCode))) {
                return true;
            }
        }
        return false;
    }
}
