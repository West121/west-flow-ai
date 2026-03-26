package com.westflow.oa.mapper;

import com.westflow.oa.api.OAExpenseBillDetailResponse;
import com.westflow.oa.api.OABillDraftListItemResponse;
import com.westflow.oa.model.OAExpenseBillRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

/**
 * OA 费用报销单的持久化与详情查询接口。
 */
@Mapper
public interface OAExpenseBillMapper {

    @Insert("""
            INSERT INTO oa_expense_bill (
              id,
              bill_no,
              scene_code,
              amount,
              reason,
              process_instance_id,
              status,
              creator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{billNo},
              #{sceneCode},
              #{amount},
              #{reason},
              #{processInstanceId},
              #{status},
              #{creatorUserId},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(OAExpenseBillRecord record);

    @Update("""
            UPDATE oa_expense_bill
            SET scene_code = #{sceneCode},
                amount = #{amount},
                reason = #{reason},
                updated_at = CURRENT_TIMESTAMP
            WHERE id = #{id}
            """)
    int updateDraft(OAExpenseBillRecord record);

    @Update("""
            UPDATE oa_expense_bill
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
              amount,
              reason,
              process_instance_id AS processInstanceId,
              status
            FROM oa_expense_bill
            WHERE id = #{billId}
            """)
    OAExpenseBillDetailResponse selectDetail(@Param("billId") String billId);

    @Select("""
            SELECT
              bill.id AS billId,
              bill.bill_no AS billNo,
              'OA_EXPENSE' AS businessType,
              CONCAT('报销申请 · ', COALESCE(bill.reason, '未命名草稿')) AS businessTitle,
              bill.scene_code AS sceneCode,
              bill.process_instance_id AS processInstanceId,
              bill.status,
              bill.creator_user_id AS creatorUserId,
              u.display_name AS creatorDisplayName,
              bill.created_at AS createdAt,
              bill.updated_at AS updatedAt
            FROM oa_expense_bill bill
            LEFT JOIN wf_user u ON u.id = bill.creator_user_id
            WHERE bill.creator_user_id = #{creatorUserId}
              AND bill.status = 'DRAFT'
            ORDER BY bill.updated_at DESC
            """)
    List<OABillDraftListItemResponse> selectDraftsByCreatorUserId(@Param("creatorUserId") String creatorUserId);
}
