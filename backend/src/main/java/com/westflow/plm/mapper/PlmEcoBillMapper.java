package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmEcoBillDetailResponse;
import com.westflow.plm.api.PlmEcoBillListItemResponse;
import com.westflow.plm.model.PlmEcoBillRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM ECO 变更执行持久化接口。
 */
@Mapper
public interface PlmEcoBillMapper {

    @Insert("""
            INSERT INTO plm_eco_execution (
              id,
              bill_no,
              scene_code,
              execution_title,
              execution_plan,
              effective_date,
              change_reason,
              process_instance_id,
              status,
              creator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{billNo},
              #{sceneCode},
              #{executionTitle},
              #{executionPlan},
              #{effectiveDate},
              #{changeReason},
              #{processInstanceId},
              #{status},
              #{creatorUserId},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmEcoBillRecord record);

    @Update("""
            UPDATE plm_eco_execution
            SET process_instance_id = #{processInstanceId},
                status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{billId}
            """)
    int updateProcessLink(
            @Param("billId") String billId,
            @Param("processInstanceId") String processInstanceId,
            @Param("status") String status
    );

    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              execution_title AS executionTitle,
              execution_plan AS executionPlan,
              effective_date AS effectiveDate,
              change_reason AS changeReason,
              process_instance_id AS processInstanceId,
              status
            FROM plm_eco_execution
            WHERE id = #{billId}
            """)
    PlmEcoBillDetailResponse selectDetail(@Param("billId") String billId);

    /**
     * 查询 ECO 业务单分页列表。
     */
    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              execution_title AS executionTitle,
              effective_date AS effectiveDate,
              change_reason AS changeReason,
              process_instance_id AS processInstanceId,
              status,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_eco_execution
            WHERE (
              #{keyword} IS NULL
              OR #{keyword} = ''
              OR LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(execution_title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(execution_plan) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    java.util.List<PlmEcoBillListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * 统计 ECO 业务单数量。
     */
    @Select("""
            SELECT COUNT(*)
            FROM plm_eco_execution
            WHERE (
              #{keyword} IS NULL
              OR #{keyword} = ''
              OR LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(execution_title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(execution_plan) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            """)
    long countPage(@Param("keyword") String keyword);
}
