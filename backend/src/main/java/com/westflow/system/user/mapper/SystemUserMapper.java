package com.westflow.system.user.mapper;

import com.westflow.system.user.api.SystemUserDetailResponse;
import com.westflow.system.user.api.SystemUserFormOptionsResponse;
import com.westflow.system.user.api.SystemUserListItemResponse;
import com.westflow.system.user.service.SystemUserService.SystemUserEntity;
import com.westflow.system.user.service.SystemUserService.SystemUserPostBinding;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("enabled") Boolean enabled,
            @Param("departmentId") String departmentId,
            @Param("postId") String postId
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
    SystemUserDetailResponse selectDetail(@Param("userId") String userId);

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

    @Select("SELECT department_id FROM wf_post WHERE id = #{postId}")
    String selectDepartmentIdByPostId(@Param("postId") String postId);

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
    int insertUser(SystemUserEntity entity);

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
    int updateUser(SystemUserEntity entity);

    @Delete("DELETE FROM wf_user_post WHERE user_id = #{userId}")
    int deleteUserPosts(@Param("userId") String userId);

    @Insert("""
            INSERT INTO wf_user_post (
              id,
              user_id,
              post_id,
              is_primary,
              created_at
            ) VALUES (
              #{id},
              #{userId},
              #{postId},
              #{primary},
              CURRENT_TIMESTAMP
            )
            """)
    int insertUserPost(SystemUserPostBinding binding);
}
