package com.westflow.system.user.mapper;

import com.westflow.system.user.response.SystemAssociatedUserResponse;
import com.westflow.system.user.response.SystemUserFormOptionsResponse;
import com.westflow.system.user.response.SystemUserListItemResponse;
import com.westflow.system.user.model.SystemUserRecord;
import com.westflow.system.user.service.SystemUserService.SystemUserPostBinding;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * 用户数据访问层，负责用户查询和写入。
 */
@Mapper
public interface SystemUserMapper {

    @Select({
            "<script>",
            "SELECT",
            "  u.id AS user_id,",
            "  u.display_name,",
            "  u.username,",
            "  u.mobile,",
            "  u.email,",
            "  d.department_name,",
            "  p.post_name,",
            "  CASE WHEN u.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status,",
            "  u.created_at",
            "FROM wf_user u",
            "LEFT JOIN wf_department d ON d.id = u.active_department_id",
            "LEFT JOIN wf_post p ON p.id = u.active_post_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(u.id) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR",
            "      LOWER(u.display_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(u.mobile) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(u.email) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(d.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(p.post_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND u.enabled = #{enabled}",
            "  </if>",
            "  <if test='departmentId != null and departmentId != \"\"'>",
            "    AND u.active_department_id = #{departmentId}",
            "  </if>",
            "  <if test='postId != null and postId != \"\"'>",
            "    AND u.active_post_id = #{postId}",
            "  </if>",
            "  <if test='!allAccess'>",
            "    <trim prefix='AND (' suffix=')' prefixOverrides='OR'>",
            "      <if test='allowedUserIds != null and allowedUserIds.size() > 0'>",
            "        OR u.id IN",
            "        <foreach collection='allowedUserIds' item='userId' open='(' separator=',' close=')'>",
            "          #{userId}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedDepartmentIds != null and allowedDepartmentIds.size() > 0'>",
            "        OR u.active_department_id IN",
            "        <foreach collection='allowedDepartmentIds' item='departmentIdItem' open='(' separator=',' close=')'>",
            "          #{departmentIdItem}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedCompanyIds != null and allowedCompanyIds.size() > 0'>",
            "        OR u.company_id IN",
            "        <foreach collection='allowedCompanyIds' item='companyIdItem' open='(' separator=',' close=')'>",
            "          #{companyIdItem}",
            "        </foreach>",
            "      </if>",
            "    </trim>",
            "  </if>",
            "</where>",
            "ORDER BY ${orderBy} ${orderDirection}",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<SystemUserListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("departmentId") String departmentId,
            @Param("postId") String postId,
            @Param("allAccess") boolean allAccess,
            @Param("allowedUserIds") List<String> allowedUserIds,
            @Param("allowedDepartmentIds") List<String> allowedDepartmentIds,
            @Param("allowedCompanyIds") List<String> allowedCompanyIds,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_user u",
            "LEFT JOIN wf_department d ON d.id = u.active_department_id",
            "LEFT JOIN wf_post p ON p.id = u.active_post_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(u.id) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR",
            "      LOWER(u.display_name) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(u.username) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(u.mobile) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(u.email) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(d.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(p.post_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='enabled != null'>",
            "    AND u.enabled = #{enabled}",
            "  </if>",
            "  <if test='departmentId != null and departmentId != \"\"'>",
            "    AND u.active_department_id = #{departmentId}",
            "  </if>",
            "  <if test='postId != null and postId != \"\"'>",
            "    AND u.active_post_id = #{postId}",
            "  </if>",
            "  <if test='!allAccess'>",
            "    <trim prefix='AND (' suffix=')' prefixOverrides='OR'>",
            "      <if test='allowedUserIds != null and allowedUserIds.size() > 0'>",
            "        OR u.id IN",
            "        <foreach collection='allowedUserIds' item='userId' open='(' separator=',' close=')'>",
            "          #{userId}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedDepartmentIds != null and allowedDepartmentIds.size() > 0'>",
            "        OR u.active_department_id IN",
            "        <foreach collection='allowedDepartmentIds' item='departmentIdItem' open='(' separator=',' close=')'>",
            "          #{departmentIdItem}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedCompanyIds != null and allowedCompanyIds.size() > 0'>",
            "        OR u.company_id IN",
            "        <foreach collection='allowedCompanyIds' item='companyIdItem' open='(' separator=',' close=')'>",
            "          #{companyIdItem}",
            "        </foreach>",
            "      </if>",
            "    </trim>",
            "  </if>",
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("departmentId") String departmentId,
            @Param("postId") String postId,
            @Param("allAccess") boolean allAccess,
            @Param("allowedUserIds") List<String> allowedUserIds,
            @Param("allowedDepartmentIds") List<String> allowedDepartmentIds,
            @Param("allowedCompanyIds") List<String> allowedCompanyIds
    );

    @Select("""
            SELECT
              u.id AS user_id,
              u.display_name,
              u.username,
              u.mobile,
              u.email,
              c.id AS company_id,
              c.company_name,
              d.id AS department_id,
              d.department_name,
              p.id AS post_id,
              p.post_name,
              u.enabled
            FROM wf_user u
            LEFT JOIN wf_company c ON c.id = u.company_id
            LEFT JOIN wf_department d ON d.id = u.active_department_id
            LEFT JOIN wf_post p ON p.id = u.active_post_id
            WHERE u.id = #{userId}
            """)
    SystemUserBaseDetailRecord selectDetail(@Param("userId") String userId);

    @Select("""
            SELECT id, company_name AS name
            FROM wf_company
            ORDER BY company_name ASC
            """)
    List<SystemUserFormOptionsResponse.CompanyOption> selectCompanyOptions();

    @Select("""
            SELECT
              p.id,
              p.post_name AS name,
              d.id AS department_id,
              d.department_name
            FROM wf_post p
            INNER JOIN wf_department d ON d.id = p.department_id
            ORDER BY d.department_name ASC, p.post_name ASC
            """)
    List<SystemUserFormOptionsResponse.PostOption> selectPostOptions();

    @Select("""
            SELECT
              r.id AS id,
              r.role_name AS name,
              r.role_code AS roleCode,
              r.role_category AS roleCategory
            FROM wf_role r
            WHERE r.enabled = TRUE
            ORDER BY r.role_name ASC
            """)
    List<SystemUserFormOptionsResponse.RoleOption> selectRoleOptions();

    @Select("""
            SELECT
              p.department_id,
              d.company_id
            FROM wf_post p
            INNER JOIN wf_department d ON d.id = p.department_id
            WHERE p.id = #{postId}
            """)
    PostContext selectPostContextByPostId(@Param("postId") String postId);

    @Select("""
            SELECT id
            FROM wf_department
            WHERE parent_department_id = #{departmentId}
            ORDER BY department_name ASC
            """)
    List<String> selectDepartmentIdsByParentId(@Param("departmentId") String departmentId);

    @Select("""
            SELECT role_id
            FROM wf_user_role
            WHERE user_id = #{userId}
            ORDER BY created_at ASC
            """)
    List<String> selectRoleIdsByUserId(@Param("userId") String userId);

    @Select("""
            SELECT
              up.id AS userPostId,
              c.id AS companyId,
              c.company_name AS companyName,
              d.id AS departmentId,
              d.department_name AS departmentName,
              p.id AS postId,
              p.post_name AS postName,
              up.is_primary AS primaryPost,
              up.enabled AS enabled
            FROM wf_user_post up
            INNER JOIN wf_post p ON p.id = up.post_id
            INNER JOIN wf_department d ON d.id = p.department_id
            INNER JOIN wf_company c ON c.id = d.company_id
            WHERE up.user_id = #{userId}
            ORDER BY up.is_primary DESC, up.created_at ASC, up.id ASC
            """)
    List<UserPostAssignmentRecord> selectAssignmentsByUserId(@Param("userId") String userId);

    @Select("""
            SELECT
              upr.user_post_id AS userPostId,
              r.id AS roleId,
              r.role_name AS roleName
            FROM wf_user_post_role upr
            INNER JOIN wf_role r ON r.id = upr.role_id
            INNER JOIN wf_user_post up ON up.id = upr.user_post_id
            WHERE up.user_id = #{userId}
              AND r.enabled = TRUE
            ORDER BY upr.created_at ASC, r.role_name ASC
            """)
    List<UserPostRoleRecord> selectAssignmentRolesByUserId(@Param("userId") String userId);

    @Select({
            "<script>",
            "SELECT DISTINCT u.id",
            "FROM wf_user u",
            "INNER JOIN wf_user_role ur ON ur.user_id = u.id",
            "INNER JOIN wf_role r ON r.id = ur.role_id",
            "WHERE u.enabled = TRUE",
            "  AND r.enabled = TRUE",
            "  AND (",
            "    r.id IN",
            "    <foreach collection='roleRefs' item='roleRef' open='(' separator=',' close=')'>",
            "      #{roleRef}",
            "    </foreach>",
            "    OR r.role_code IN",
            "    <foreach collection='roleRefs' item='roleRef' open='(' separator=',' close=')'>",
            "      #{roleRef}",
            "    </foreach>",
            "  )",
            "ORDER BY u.id ASC",
            "</script>"
    })
    List<String> selectEnabledUserIdsByRoleRefs(@Param("roleRefs") List<String> roleRefs);

    @Select({
            "<script>",
            "SELECT DISTINCT u.id",
            "FROM wf_user u",
            "WHERE u.enabled = TRUE",
            "  AND u.active_department_id IN",
            "  <foreach collection='departmentIds' item='departmentId' open='(' separator=',' close=')'>",
            "    #{departmentId}",
            "  </foreach>",
            "ORDER BY u.id ASC",
            "</script>"
    })
    List<String> selectEnabledUserIdsByDepartmentIds(@Param("departmentIds") List<String> departmentIds);

    @Select({
            "<script>",
            "SELECT DISTINCT",
            "  u.id AS user_id,",
            "  u.display_name,",
            "  u.username,",
            "  d.department_name,",
            "  p.post_name,",
            "  CASE WHEN u.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status",
            "FROM wf_user u",
            "LEFT JOIN wf_department d ON d.id = u.active_department_id",
            "LEFT JOIN wf_post p ON p.id = u.active_post_id",
            "<where>",
            "  u.active_department_id = #{departmentId}",
            "  <if test='!allAccess'>",
            "    <trim prefix='AND (' suffix=')' prefixOverrides='OR'>",
            "      <if test='allowedUserIds != null and allowedUserIds.size() > 0'>",
            "        OR u.id IN",
            "        <foreach collection='allowedUserIds' item='userId' open='(' separator=',' close=')'>",
            "          #{userId}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedDepartmentIds != null and allowedDepartmentIds.size() > 0'>",
            "        OR u.active_department_id IN",
            "        <foreach collection='allowedDepartmentIds' item='departmentIdItem' open='(' separator=',' close=')'>",
            "          #{departmentIdItem}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedCompanyIds != null and allowedCompanyIds.size() > 0'>",
            "        OR u.company_id IN",
            "        <foreach collection='allowedCompanyIds' item='companyIdItem' open='(' separator=',' close=')'>",
            "          #{companyIdItem}",
            "        </foreach>",
            "      </if>",
            "    </trim>",
            "  </if>",
            "</where>",
            "ORDER BY u.display_name ASC, u.id ASC",
            "</script>"
    })
    List<SystemAssociatedUserResponse> selectAssociatedUsersByDepartmentId(
            @Param("departmentId") String departmentId,
            @Param("allAccess") boolean allAccess,
            @Param("allowedUserIds") List<String> allowedUserIds,
            @Param("allowedDepartmentIds") List<String> allowedDepartmentIds,
            @Param("allowedCompanyIds") List<String> allowedCompanyIds
    );

    @Select({
            "<script>",
            "SELECT DISTINCT",
            "  u.id AS user_id,",
            "  u.display_name,",
            "  u.username,",
            "  d.department_name,",
            "  p.post_name,",
            "  CASE WHEN u.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status",
            "FROM wf_user_post up",
            "INNER JOIN wf_user u ON u.id = up.user_id",
            "LEFT JOIN wf_department d ON d.id = u.active_department_id",
            "LEFT JOIN wf_post p ON p.id = u.active_post_id",
            "<where>",
            "  up.post_id = #{postId}",
            "  <if test='!allAccess'>",
            "    <trim prefix='AND (' suffix=')' prefixOverrides='OR'>",
            "      <if test='allowedUserIds != null and allowedUserIds.size() > 0'>",
            "        OR u.id IN",
            "        <foreach collection='allowedUserIds' item='userId' open='(' separator=',' close=')'>",
            "          #{userId}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedDepartmentIds != null and allowedDepartmentIds.size() > 0'>",
            "        OR u.active_department_id IN",
            "        <foreach collection='allowedDepartmentIds' item='departmentIdItem' open='(' separator=',' close=')'>",
            "          #{departmentIdItem}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedCompanyIds != null and allowedCompanyIds.size() > 0'>",
            "        OR u.company_id IN",
            "        <foreach collection='allowedCompanyIds' item='companyIdItem' open='(' separator=',' close=')'>",
            "          #{companyIdItem}",
            "        </foreach>",
            "      </if>",
            "    </trim>",
            "  </if>",
            "</where>",
            "ORDER BY u.display_name ASC, u.id ASC",
            "</script>"
    })
    List<SystemAssociatedUserResponse> selectAssociatedUsersByPostId(
            @Param("postId") String postId,
            @Param("allAccess") boolean allAccess,
            @Param("allowedUserIds") List<String> allowedUserIds,
            @Param("allowedDepartmentIds") List<String> allowedDepartmentIds,
            @Param("allowedCompanyIds") List<String> allowedCompanyIds
    );

    @Select({
            "<script>",
            "SELECT DISTINCT",
            "  u.id AS user_id,",
            "  u.display_name,",
            "  u.username,",
            "  d.department_name,",
            "  p.post_name,",
            "  CASE WHEN u.enabled THEN 'ENABLED' ELSE 'DISABLED' END AS status",
            "FROM wf_user_role ur",
            "INNER JOIN wf_user u ON u.id = ur.user_id",
            "LEFT JOIN wf_department d ON d.id = u.active_department_id",
            "LEFT JOIN wf_post p ON p.id = u.active_post_id",
            "<where>",
            "  ur.role_id = #{roleId}",
            "  <if test='!allAccess'>",
            "    <trim prefix='AND (' suffix=')' prefixOverrides='OR'>",
            "      <if test='allowedUserIds != null and allowedUserIds.size() > 0'>",
            "        OR u.id IN",
            "        <foreach collection='allowedUserIds' item='userId' open='(' separator=',' close=')'>",
            "          #{userId}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedDepartmentIds != null and allowedDepartmentIds.size() > 0'>",
            "        OR u.active_department_id IN",
            "        <foreach collection='allowedDepartmentIds' item='departmentIdItem' open='(' separator=',' close=')'>",
            "          #{departmentIdItem}",
            "        </foreach>",
            "      </if>",
            "      <if test='allowedCompanyIds != null and allowedCompanyIds.size() > 0'>",
            "        OR u.company_id IN",
            "        <foreach collection='allowedCompanyIds' item='companyIdItem' open='(' separator=',' close=')'>",
            "          #{companyIdItem}",
            "        </foreach>",
            "      </if>",
            "    </trim>",
            "  </if>",
            "</where>",
            "ORDER BY u.display_name ASC, u.id ASC",
            "</script>"
    })
    List<SystemAssociatedUserResponse> selectAssociatedUsersByRoleId(
            @Param("roleId") String roleId,
            @Param("allAccess") boolean allAccess,
            @Param("allowedUserIds") List<String> allowedUserIds,
            @Param("allowedDepartmentIds") List<String> allowedDepartmentIds,
            @Param("allowedCompanyIds") List<String> allowedCompanyIds
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_user",
            "WHERE username = #{username}",
            "  <if test='excludeUserId != null and excludeUserId != \"\"'>",
            "    AND id != #{excludeUserId}",
            "  </if>",
            "</script>"
    })
    Long countByUsername(@Param("username") String username, @Param("excludeUserId") String excludeUserId);

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_role",
            "WHERE id IN",
            "  <foreach collection='roleIds' item='roleId' open='(' separator=',' close=')'>",
            "    #{roleId}",
            "  </foreach>",
            "  AND enabled = TRUE",
            "</script>"
    })
    long countExistingRoles(@Param("roleIds") List<String> roleIds);

    @Insert("""
            INSERT INTO wf_user (
              id,
              username,
              display_name,
              mobile,
              email,
              avatar,
              company_id,
              active_department_id,
              active_post_id,
              enabled,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{username},
              #{displayName},
              #{mobile},
              #{email},
              #{avatar},
              #{companyId},
              #{activeDepartmentId},
              #{activePostId},
              #{enabled},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertUser(SystemUserRecord entity);

    @Update("""
            UPDATE wf_user
            SET display_name = #{displayName},
                username = #{username},
                mobile = #{mobile},
                email = #{email},
                company_id = #{companyId},
                active_department_id = #{activeDepartmentId},
                active_post_id = #{activePostId},
                enabled = #{enabled},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateUser(SystemUserRecord entity);

    @Delete("DELETE FROM wf_user WHERE id = #{userId}")
    int deleteUser(@Param("userId") String userId);

    @Delete("DELETE FROM wf_user_post WHERE user_id = #{userId}")
    int deleteUserPosts(@Param("userId") String userId);

    @Delete("""
            DELETE FROM wf_user_post_role
            WHERE user_post_id IN (
              SELECT id
              FROM wf_user_post
              WHERE user_id = #{userId}
            )
            """)
    int deleteUserPostRoles(@Param("userId") String userId);

    @Delete("DELETE FROM wf_user_role WHERE user_id = #{userId}")
    int deleteUserRoles(@Param("userId") String userId);

    @Insert("""
            INSERT INTO wf_user_post (
              id,
              user_id,
              post_id,
              is_primary,
              enabled,
              created_at
            ) VALUES (
              #{id},
              #{userId},
              #{postId},
              #{primary},
              #{enabled},
              CURRENT_TIMESTAMP
            )
            """)
    int insertUserPost(SystemUserPostBinding binding);

    @Insert("""
            INSERT INTO wf_user_post_role (
              id,
              user_post_id,
              role_id,
              created_at
            ) VALUES (
              #{id},
              #{userPostId},
              #{roleId},
              CURRENT_TIMESTAMP
            )
            """)
    int insertUserPostRole(SystemUserPostRoleBinding binding);

    @Insert("""
            INSERT INTO wf_user_role (
              id,
              user_id,
              role_id,
              created_at
            ) VALUES (
              #{id},
              #{userId},
              #{roleId},
              CURRENT_TIMESTAMP
            )
            """)
    int insertUserRole(SystemUserRoleBinding binding);

    record PostContext(
            String departmentId,
            String companyId
    ) {
    }

    record SystemUserBaseDetailRecord(
            String userId,
            String displayName,
            String username,
            String mobile,
            String email,
            String companyId,
            String companyName,
            String departmentId,
            String departmentName,
            String postId,
            String postName,
            boolean enabled
    ) {
    }

    record SystemUserRoleBinding(
            String id,
            String userId,
            String roleId
    ) {
    }

    record SystemUserPostRoleBinding(
            String id,
            String userPostId,
            String roleId
    ) {
    }

    record UserPostAssignmentRecord(
            String userPostId,
            String companyId,
            String companyName,
            String departmentId,
            String departmentName,
            String postId,
            String postName,
            Boolean primaryPost,
            Boolean enabled
    ) {
    }

    record UserPostRoleRecord(
            String userPostId,
            String roleId,
            String roleName
    ) {
    }
}
