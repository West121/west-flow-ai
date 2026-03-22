package com.westflow.identity.service;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.common.error.ContractException;
import com.westflow.identity.dto.CurrentUserResponse;
import com.westflow.identity.dto.LoginRequest;
import com.westflow.identity.dto.LoginResponse;
import com.westflow.identity.mapper.IdentityAccessMapper;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class FixtureAuthService {

    private static final String ACTIVE_POST_ID = "activePostId";

    private final Map<String, FixtureUser> usersByUsername;
    private final Map<String, FixtureUser> usersById;
    private final IdentityAccessMapper identityAccessMapper;

    public FixtureAuthService(IdentityAccessMapper identityAccessMapper) {
        this.identityAccessMapper = identityAccessMapper;
        FixtureUser zhangsan = new FixtureUser(
                "usr_001",
                "zhangsan",
                "password123",
                "张三",
                "13800000000",
                "zhangsan@example.com",
                "",
                "cmp_001",
                "post_001",
                Map.of(
                        "post_001", new PostFixture("post_001", "dept_001", "部门经理"),
                        "post_002", new PostFixture("post_002", "dept_002", "项目助理")
                ),
                List.of(new CurrentUserResponse.PartTimePost("post_002", "dept_002", "项目助理")),
                List.of(new CurrentUserResponse.Delegation("usr_002", "usr_001", "ACTIVE")),
                List.of("ai:copilot:open", "ai:process:start", "ai:task:handle")
        );

        FixtureUser lisi = new FixtureUser(
                "usr_002",
                "lisi",
                "password123",
                "李四",
                "13900000000",
                "lisi@example.com",
                "",
                "cmp_001",
                "post_003",
                Map.of("post_003", new PostFixture("post_003", "dept_003", "普通员工")),
                List.of(),
                List.of(),
                List.of("ai:copilot:open")
        );

        this.usersByUsername = Map.of(zhangsan.username(), zhangsan, lisi.username(), lisi);
        this.usersById = Map.of(zhangsan.userId(), zhangsan, lisi.userId(), lisi);
    }

    public LoginResponse login(LoginRequest request) {
        FixtureUser user = usersByUsername.get(request.username());
        if (user == null || !user.password().equals(request.password())) {
            throw new ContractException("AUTH.UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "用户名或密码错误");
        }

        StpUtil.login(user.userId());
        StpUtil.getTokenSession().set(ACTIVE_POST_ID, user.primaryPostId());

        return new LoginResponse(StpUtil.getTokenValue(), "Bearer", StpUtil.getTokenTimeout());
    }

    public CurrentUserResponse currentUser() {
        String loginId = StpUtil.getLoginIdAsString();
        FixtureUser user = getUserById(loginId);
        String activePostId = StpUtil.getTokenSession().getString(ACTIVE_POST_ID);
        if (activePostId == null || activePostId.isBlank()) {
            activePostId = user.primaryPostId();
        }
        PostFixture activePost = user.posts().get(activePostId);
        if (activePost == null) {
            throw new ContractException("AUTH.FORBIDDEN", HttpStatus.FORBIDDEN, "当前岗位上下文不存在");
        }

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
                identityAccessMapper.selectRoleCodesByUserId(user.userId()),
                identityAccessMapper.selectPermissionsByUserId(user.userId()),
                identityAccessMapper.selectDataScopesByUserId(user.userId()),
                user.partTimePosts(),
                user.delegations(),
                user.aiCapabilities(),
                identityAccessMapper.selectMenusByUserId(user.userId())
        );
    }

    public void switchContext(String activePostId) {
        FixtureUser user = getUserById(StpUtil.getLoginIdAsString());
        if (!user.posts().containsKey(activePostId)) {
            throw new ContractException(
                    "VALIDATION.FIELD_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "岗位上下文不存在",
                    Map.of("activePostId", activePostId)
            );
        }
        StpUtil.getTokenSession().set(ACTIVE_POST_ID, activePostId);
    }

    public List<String> permissionsByUserId(String userId) {
        getUserById(userId);
        return identityAccessMapper.selectPermissionsByUserId(userId);
    }

    public List<String> rolesByUserId(String userId) {
        getUserById(userId);
        return identityAccessMapper.selectRoleCodesByUserId(userId);
    }

    private FixtureUser getUserById(String userId) {
        FixtureUser user = usersById.get(userId);
        if (user == null) {
            throw new ContractException("AUTH.UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "登录用户不存在");
        }
        return user;
    }

    private record FixtureUser(
            String userId,
            String username,
            String password,
            String displayName,
            String mobile,
            String email,
            String avatar,
            String companyId,
            String primaryPostId,
            Map<String, PostFixture> posts,
            List<CurrentUserResponse.PartTimePost> partTimePosts,
            List<CurrentUserResponse.Delegation> delegations,
            List<String> aiCapabilities
    ) {
    }

    private record PostFixture(
            String postId,
            String departmentId,
            String postName
    ) {
    }
}
