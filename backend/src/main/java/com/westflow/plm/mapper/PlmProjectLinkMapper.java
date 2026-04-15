package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmProjectLinkResponse;
import com.westflow.plm.model.PlmProjectLinkRecord;
import java.util.List;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目关联对象持久化接口。
 */
@Mapper
public interface PlmProjectLinkMapper {

    @Delete("""
            DELETE FROM plm_project_link
            WHERE project_id = #{projectId}
            """)
    int deleteByProjectId(@Param("projectId") String projectId);

    @Insert("""
            INSERT INTO plm_project_link (
              id,
              project_id,
              link_type,
              target_business_type,
              target_id,
              target_no,
              target_title,
              target_status,
              target_href,
              summary,
              sort_order,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{projectId},
              #{linkType},
              #{targetBusinessType},
              #{targetId},
              #{targetNo},
              #{targetTitle},
              #{targetStatus},
              #{targetHref},
              #{summary},
              #{sortOrder},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmProjectLinkRecord record);

    @Select("""
            SELECT
              id,
              link_type AS linkType,
              target_business_type AS targetBusinessType,
              target_id AS targetId,
              target_no AS targetNo,
              target_title AS targetTitle,
              target_status AS targetStatus,
              target_href AS targetHref,
              summary,
              sort_order AS sortOrder
            FROM plm_project_link
            WHERE project_id = #{projectId}
            ORDER BY sort_order ASC, created_at ASC
            """)
    List<PlmProjectLinkResponse> selectByProjectId(@Param("projectId") String projectId);
}
