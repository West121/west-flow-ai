package com.westflow.system.agent.mapper;

import com.westflow.system.agent.api.SystemAgentDetailResponse;
import com.westflow.system.agent.api.SystemAgentFormOptionsResponse;
import com.westflow.system.agent.api.SystemAgentListItemResponse;
import com.westflow.system.agent.service.SystemAgentService.SystemAgentEntity;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SystemAgentMapper {

    @Select({
            "<script>",
            "SELECT",
            "  d.id AS agent_id,",
            "  d.principal_user_id,",
            "  pu.display_name AS principal_display_name,",
            "  pu.username AS principal_username,",
            "  pd.department_name AS principal_department_name,",
            "  pp.post_name AS principal_post_name,",
            "  d.delegate_user_id,",
            "  du.display_name AS delegate_display_name,",
            "  du.username AS delegate_username,",
            "  dd.department_name AS delegate_department_name,",
            "  dp.post_name AS delegate_post_name,",
            "  d.status,",
            "  d.remark,",
            "  d.created_at,",
            "  d.updated_at",
            "FROM wf_delegation d",
            "LEFT JOIN wf_user pu ON pu.id = d.principal_user_id",
            "LEFT JOIN wf_department pd ON pd.id = pu.active_department_id",
            "LEFT JOIN wf_post pp ON pp.id = pu.active_post_id",
            "LEFT JOIN wf_user du ON du.id = d.delegate_user_id",
            "LEFT JOIN wf_department dd ON dd.id = du.active_department_id",
            "LEFT JOIN wf_post dp ON dp.id = du.active_post_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(COALESCE(pu.display_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pu.username, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(du.display_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(du.username, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pd.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(dd.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pp.post_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(dp.post_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(d.remark, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND d.status = #{status}",
            "  </if>",
            "</where>",
            "ORDER BY ${orderBy} ${orderDirection}",
            "LIMIT #{limit} OFFSET #{offset}",
            "</script>"
    })
    List<SystemAgentListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("orderBy") String orderBy,
            @Param("orderDirection") String orderDirection,
            @Param("limit") long limit,
            @Param("offset") long offset
    );

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_delegation d",
            "LEFT JOIN wf_user pu ON pu.id = d.principal_user_id",
            "LEFT JOIN wf_department pd ON pd.id = pu.active_department_id",
            "LEFT JOIN wf_post pp ON pp.id = pu.active_post_id",
            "LEFT JOIN wf_user du ON du.id = d.delegate_user_id",
            "LEFT JOIN wf_department dd ON dd.id = du.active_department_id",
            "LEFT JOIN wf_post dp ON dp.id = du.active_post_id",
            "<where>",
            "  <if test='keyword != null and keyword != \"\"'>",
            "    AND (",
            "      LOWER(COALESCE(pu.display_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pu.username, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(du.display_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(du.username, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pd.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(dd.department_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(pp.post_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(dp.post_name, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "      OR LOWER(COALESCE(d.remark, '')) LIKE CONCAT('%', LOWER(#{keyword}), '%')",
            "    )",
            "  </if>",
            "  <if test='status != null and status != \"\"'>",
            "    AND d.status = #{status}",
            "  </if>",
            "</where>",
            "</script>"
    })
    long countPage(
            @Param("keyword") String keyword,
            @Param("status") String status
    );

    @Select("""
            SELECT
              d.id AS agent_id,
              d.principal_user_id,
              pu.display_name AS principal_display_name,
              pu.username AS principal_username,
              pd.department_name AS principal_department_name,
              pp.post_name AS principal_post_name,
              d.delegate_user_id,
              du.display_name AS delegate_display_name,
              du.username AS delegate_username,
              dd.department_name AS delegate_department_name,
              dp.post_name AS delegate_post_name,
              d.status,
              d.remark,
              d.created_at,
              d.updated_at
            FROM wf_delegation d
            LEFT JOIN wf_user pu ON pu.id = d.principal_user_id
            LEFT JOIN wf_department pd ON pd.id = pu.active_department_id
            LEFT JOIN wf_post pp ON pp.id = pu.active_post_id
            LEFT JOIN wf_user du ON du.id = d.delegate_user_id
            LEFT JOIN wf_department dd ON dd.id = du.active_department_id
            LEFT JOIN wf_post dp ON dp.id = du.active_post_id
            WHERE d.id = #{agentId}
            """)
    SystemAgentDetailResponse selectDetail(@Param("agentId") String agentId);

    @Select("""
            SELECT
              u.id AS userId,
              u.display_name,
              u.username,
              d.department_name,
              p.post_name,
              u.enabled
            FROM wf_user u
            LEFT JOIN wf_department d ON d.id = u.active_department_id
            LEFT JOIN wf_post p ON p.id = u.active_post_id
            WHERE u.enabled = TRUE
            ORDER BY u.display_name ASC
            """)
    List<SystemAgentFormOptionsResponse.UserOption> selectUserOptions();

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM wf_delegation",
            "WHERE principal_user_id = #{principalUserId}",
            "  AND delegate_user_id = #{delegateUserId}",
            "  <if test='excludeAgentId != null and excludeAgentId != \"\"'>",
            "    AND id != #{excludeAgentId}",
            "  </if>",
            "</script>"
    })
    Long countByPrincipalAndDelegate(
            @Param("principalUserId") String principalUserId,
            @Param("delegateUserId") String delegateUserId,
            @Param("excludeAgentId") String excludeAgentId
    );

    @Insert("""
            INSERT INTO wf_delegation (
              id,
              principal_user_id,
              delegate_user_id,
              status,
              remark,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{principalUserId},
              #{delegateUserId},
              #{status},
              #{remark},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertDelegation(SystemAgentEntity entity);

    @Update("""
            UPDATE wf_delegation
            SET principal_user_id = #{principalUserId},
                delegate_user_id = #{delegateUserId},
                status = #{status},
                remark = #{remark},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDelegation(SystemAgentEntity entity);
}
