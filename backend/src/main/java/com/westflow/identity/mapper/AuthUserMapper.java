package com.westflow.identity.mapper;

import com.westflow.identity.dto.CurrentUserResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 认证用户查询与登录状态更新映射。
 */
@Mapper
public interface AuthUserMapper {

    /**
     * 按用户名查询登录用户。
     */
    @Select("""
            SELECT
              id AS userId,
              username,
              display_name AS displayName,
              mobile,
              email,
              avatar,
              company_id AS companyId,
              active_department_id AS activeDepartmentId,
              active_post_id AS activePostId,
              password_hash AS passwordHash,
              enabled,
              login_enabled AS loginEnabled,
              failed_login_count AS failedLoginCount,
              locked_until AS lockedUntil
            FROM wf_user
            WHERE username = #{username}
            """)
    AuthUserRecord selectByUsername(@Param("username") String username);

    /**
     * 按用户 ID 查询登录用户。
     */
    @Select("""
            SELECT
              id AS userId,
              username,
              display_name AS displayName,
              mobile,
              email,
              avatar,
              company_id AS companyId,
              active_department_id AS activeDepartmentId,
              active_post_id AS activePostId,
              password_hash AS passwordHash,
              enabled,
              login_enabled AS loginEnabled,
              failed_login_count AS failedLoginCount,
              locked_until AS lockedUntil
            FROM wf_user
            WHERE id = #{userId}
            """)
    AuthUserRecord selectByUserId(@Param("userId") String userId);

    /**
     * 查询用户拥有的岗位上下文。
     */
    @Select("""
            SELECT
              up.post_id AS postId,
              p.department_id AS departmentId,
              p.post_name AS postName,
              up.is_primary AS primaryPost
            FROM wf_user_post up
            INNER JOIN wf_post p ON p.id = up.post_id
            WHERE up.user_id = #{userId}
            ORDER BY up.is_primary DESC, up.created_at ASC
            """)
    List<UserPostContextRecord> selectPostContextsByUserId(@Param("userId") String userId);

    /**
     * 查询用户兼职岗位。
     */
    @Select("""
            SELECT
              up.post_id AS postId,
              p.department_id AS departmentId,
              p.post_name AS postName
            FROM wf_user_post up
            INNER JOIN wf_post p ON p.id = up.post_id
            WHERE up.user_id = #{userId}
              AND up.is_primary = FALSE
            ORDER BY up.created_at ASC
            """)
    List<CurrentUserResponse.PartTimePost> selectPartTimePostsByUserId(@Param("userId") String userId);

    /**
     * 查询用户 AI 能力编码。
     */
    @Select("""
            SELECT capability_code
            FROM wf_user_ai_capability
            WHERE user_id = #{userId}
            ORDER BY capability_code ASC
            """)
    List<String> selectAiCapabilitiesByUserId(@Param("userId") String userId);

    /**
     * 更新登录失败计数与锁定时间。
     */
    @Update("""
            UPDATE wf_user
            SET failed_login_count = #{failedLoginCount},
                locked_until = #{lockedUntil}
            WHERE id = #{userId}
            """)
    int updateLoginFailureState(
            @Param("userId") String userId,
            @Param("failedLoginCount") int failedLoginCount,
            @Param("lockedUntil") OffsetDateTime lockedUntil
    );

    /**
     * 登录成功后重置失败状态并记录最后登录时间。
     */
    @Update("""
            UPDATE wf_user
            SET failed_login_count = 0,
                locked_until = NULL,
                last_login_at = CURRENT_TIMESTAMP
            WHERE id = #{userId}
            """)
    int markLoginSuccess(@Param("userId") String userId);

    /**
     * 已认证用户基础信息。
     */
    record AuthUserRecord(
            String userId,
            String username,
            String displayName,
            String mobile,
            String email,
            String avatar,
            String companyId,
            String activeDepartmentId,
            String activePostId,
            String passwordHash,
            Boolean enabled,
            Boolean loginEnabled,
            Integer failedLoginCount,
            OffsetDateTime lockedUntil
    ) {
    }

    /**
     * 用户岗位上下文。
     */
    record UserPostContextRecord(
            String postId,
            String departmentId,
            String postName,
            Boolean primaryPost
    ) {
    }
}
