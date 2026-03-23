package com.westflow.plm.mapper;

import com.westflow.plm.api.PlmEcrBillDetailResponse;
import com.westflow.plm.api.PlmEcrBillListItemResponse;
import com.westflow.plm.model.PlmEcrBillRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * PLM ECR 变更申请持久化接口。
 */
@Mapper
public interface PlmEcrBillMapper {

    @Insert("""
            INSERT INTO plm_ecr_change (
              id,
              bill_no,
              scene_code,
              change_title,
              change_reason,
              affected_product_code,
              priority_level,
              process_instance_id,
              status,
              creator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{billNo},
              #{sceneCode},
              #{changeTitle},
              #{changeReason},
              #{affectedProductCode},
              #{priorityLevel},
              #{processInstanceId},
              #{status},
              #{creatorUserId},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(PlmEcrBillRecord record);

    @Update("""
            UPDATE plm_ecr_change
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
              change_title AS changeTitle,
              change_reason AS changeReason,
              affected_product_code AS affectedProductCode,
              priority_level AS priorityLevel,
              process_instance_id AS processInstanceId,
              status,
              CONCAT('影响产品 ', COALESCE(affected_product_code, '--'), ' · ', COALESCE(priority_level, '--')) AS detailSummary,
              CONCAT(status, ' · 当前节点 ', COALESCE(process_instance_id, '待同步')) AS approvalSummary,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_ecr_change
            WHERE id = #{billId}
            """)
    PlmEcrBillDetailResponse selectDetail(@Param("billId") String billId);

    /**
     * 查询 ECR 业务单分页列表。
     */
    @Select("""
            SELECT
              id AS billId,
              bill_no AS billNo,
              scene_code AS sceneCode,
              change_title AS changeTitle,
              affected_product_code AS affectedProductCode,
              priority_level AS priorityLevel,
              process_instance_id AS processInstanceId,
              status,
              CONCAT('影响产品 ', COALESCE(affected_product_code, '--'), ' · ', COALESCE(priority_level, '--')) AS detailSummary,
              CONCAT(status, ' · 当前节点 ', COALESCE(process_instance_id, '待同步')) AS approvalSummary,
              creator_user_id AS creatorUserId,
              created_at AS createdAt,
              updated_at AS updatedAt
            FROM plm_ecr_change
            WHERE (
              #{keyword} IS NULL
              OR #{keyword} = ''
              OR LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(change_title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(change_reason) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            AND (
              #{status} IS NULL
              OR status = #{status}
            )
            ORDER BY created_at DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    java.util.List<PlmEcrBillListItemResponse> selectPage(
            @Param("keyword") String keyword,
            @Param("status") String status,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    /**
     * 统计 ECR 业务单数量。
     */
    @Select("""
            SELECT COUNT(*)
            FROM plm_ecr_change
            WHERE (
              #{keyword} IS NULL
              OR #{keyword} = ''
              OR LOWER(bill_no) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(change_title) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(change_reason) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
              OR LOWER(scene_code) LIKE LOWER(CONCAT('%', #{keyword}, '%'))
            )
            AND (
              #{status} IS NULL
              OR status = #{status}
            )
            """)
    long countPage(@Param("keyword") String keyword, @Param("status") String status);
}
