package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmProjectMemberResponse;
import com.westflow.plm.model.PlmProjectMemberRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目成员持久化接口。
 */
@Mapper
public interface PlmProjectMemberMapper {

    @Delete("""
            DELETE FROM plm_project_member
            WHERE project_id = #{projectId}
            """)
    int deleteByProjectId(@Param("projectId") String projectId);

    @Insert("""
            INSERT INTO plm_project_member (
              id,
              project_id,
              user_id,
              role_code,
              role_label,
              responsibility_summary,
              sort_order,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{projectId},
              #{userId},
              #{roleCode},
              #{roleLabel},
              #{responsibilitySummary},
              #{sortOrder},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmProjectMemberRecord record);

    @Select("""
            SELECT
              pm.id,
              pm.user_id AS userId,
              u.display_name AS displayName,
              pm.role_code AS roleCode,
              pm.role_label AS roleLabel,
              pm.responsibility_summary AS responsibilitySummary,
              pm.sort_order AS sortOrder
            FROM plm_project_member pm
            LEFT JOIN wf_user u ON u.id = pm.user_id
            WHERE pm.project_id = #{projectId}
            ORDER BY pm.sort_order ASC, pm.created_at ASC
            """)
    List<PlmProjectMemberResponse> selectByProjectId(@Param("projectId") String projectId);
}
