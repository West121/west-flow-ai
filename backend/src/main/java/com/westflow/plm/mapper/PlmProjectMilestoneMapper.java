package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmProjectMilestoneResponse;
import com.westflow.plm.model.PlmProjectMilestoneRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目里程碑持久化接口。
 */
@Mapper
public interface PlmProjectMilestoneMapper {

    @Delete("""
            DELETE FROM plm_project_milestone
            WHERE project_id = #{projectId}
            """)
    int deleteByProjectId(@Param("projectId") String projectId);

    @Insert("""
            INSERT INTO plm_project_milestone (
              id,
              project_id,
              milestone_code,
              milestone_name,
              status,
              owner_user_id,
              planned_at,
              actual_at,
              summary,
              sort_order,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{projectId},
              #{milestoneCode},
              #{milestoneName},
              #{status},
              #{ownerUserId},
              #{plannedAt},
              #{actualAt},
              #{summary},
              #{sortOrder},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmProjectMilestoneRecord record);

    @Select("""
            SELECT
              pm.id,
              pm.milestone_code AS milestoneCode,
              pm.milestone_name AS milestoneName,
              pm.status,
              pm.owner_user_id AS ownerUserId,
              owner.display_name AS ownerDisplayName,
              pm.planned_at AS plannedAt,
              pm.actual_at AS actualAt,
              pm.summary,
              pm.sort_order AS sortOrder
            FROM plm_project_milestone pm
            LEFT JOIN wf_user owner ON owner.id = pm.owner_user_id
            WHERE pm.project_id = #{projectId}
            ORDER BY pm.sort_order ASC, pm.created_at ASC
            """)
    List<PlmProjectMilestoneResponse> selectByProjectId(@Param("projectId") String projectId);
}
