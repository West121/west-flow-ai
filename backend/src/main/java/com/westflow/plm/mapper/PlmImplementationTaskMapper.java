package com.westflow.plm.mapper;

import com.westflow.plm.model.PlmImplementationTaskRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM 实施任务持久化接口。
 */
@Mapper
public interface PlmImplementationTaskMapper {

    @Insert("""
            INSERT INTO plm_implementation_task (
              id,
              business_type,
              bill_id,
              task_no,
              task_title,
              task_type,
              owner_user_id,
              status,
              planned_start_at,
              planned_end_at,
              started_at,
              completed_at,
              result_summary,
              verification_required,
              sort_order,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{businessType},
              #{billId},
              #{taskNo},
              #{taskTitle},
              #{taskType},
              #{ownerUserId},
              #{status},
              #{plannedStartAt},
              #{plannedEndAt},
              #{startedAt},
              #{completedAt},
              #{resultSummary},
              #{verificationRequired},
              #{sortOrder},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmImplementationTaskRecord record);

    @Update("""
            UPDATE plm_implementation_task
            SET task_no = #{taskNo},
                task_title = #{taskTitle},
                task_type = #{taskType},
                owner_user_id = #{ownerUserId},
                status = #{status},
                planned_start_at = #{plannedStartAt},
                planned_end_at = #{plannedEndAt},
                started_at = #{startedAt},
                completed_at = #{completedAt},
                result_summary = #{resultSummary},
                verification_required = #{verificationRequired},
                sort_order = #{sortOrder},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int update(PlmImplementationTaskRecord record);

    @Update("""
            UPDATE plm_implementation_task
            SET status = #{status},
                started_at = #{startedAt},
                completed_at = #{completedAt},
                result_summary = #{resultSummary},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateStatus(
            @Param("id") String id,
            @Param("status") String status,
            @Param("startedAt") java.time.LocalDateTime startedAt,
            @Param("completedAt") java.time.LocalDateTime completedAt,
            @Param("resultSummary") String resultSummary
    );

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              bill_id AS billId,
              task_no AS taskNo,
              task_title AS taskTitle,
              task_type AS taskType,
              owner_user_id AS ownerUserId,
              status,
              planned_start_at AS plannedStartAt,
              planned_end_at AS plannedEndAt,
              started_at AS startedAt,
              completed_at AS completedAt,
              result_summary AS resultSummary,
              verification_required AS verificationRequired,
              sort_order AS sortOrder,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_implementation_task
            WHERE id = #{id}
            """)
    PlmImplementationTaskRecord selectById(@Param("id") String id);

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              bill_id AS billId,
              task_no AS taskNo,
              task_title AS taskTitle,
              task_type AS taskType,
              owner_user_id AS ownerUserId,
              status,
              planned_start_at AS plannedStartAt,
              planned_end_at AS plannedEndAt,
              started_at AS startedAt,
              completed_at AS completedAt,
              result_summary AS resultSummary,
              verification_required AS verificationRequired,
              sort_order AS sortOrder,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_implementation_task
            WHERE business_type = #{businessType}
              AND bill_id = #{billId}
            ORDER BY sort_order ASC, created_at ASC, id ASC
            """)
    List<PlmImplementationTaskRecord> selectByBusinessTypeAndBillId(
            @Param("businessType") String businessType,
            @Param("billId") String billId
    );

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              bill_id AS billId,
              task_no AS taskNo,
              task_title AS taskTitle,
              task_type AS taskType,
              owner_user_id AS ownerUserId,
              status,
              planned_start_at AS plannedStartAt,
              planned_end_at AS plannedEndAt,
              started_at AS startedAt,
              completed_at AS completedAt,
              result_summary AS resultSummary,
              verification_required AS verificationRequired,
              sort_order AS sortOrder,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_implementation_task
            ORDER BY created_at ASC, sort_order ASC, id ASC
            """)
    List<PlmImplementationTaskRecord> selectAll();
}
