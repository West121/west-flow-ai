package com.westflow.oa.mapper;

import com.westflow.oa.api.OACommonRequestBillDetailResponse;
import com.westflow.oa.model.OACommonRequestBillRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface OACommonRequestBillMapper {

    @Insert("""
            INSERT INTO oa_common_request_bill (
              id,
              bill_no,
              scene_code,
              title,
              content,
              process_instance_id,
              status,
              creator_user_id,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{billNo},
              #{sceneCode},
              #{title},
              #{content},
              #{processInstanceId},
              #{status},
              #{creatorUserId},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insert(OACommonRequestBillRecord record);

    @Update("""
            UPDATE oa_common_request_bill
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
              title,
              content,
              process_instance_id AS processInstanceId,
              status
            FROM oa_common_request_bill
            WHERE id = #{billId}
            """)
    OACommonRequestBillDetailResponse selectDetail(@Param("billId") String billId);
}
