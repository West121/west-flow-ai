package com.westflow.processbinding.mapper;

import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

@Mapper
// 业务流程关联表的写入接口。
public interface BusinessProcessLinkMapper {

    @Insert("""
            INSERT INTO wf_business_process_link (
              id,
              business_type,
              business_id,
              process_instance_id,
              process_definition_id,
              start_user_id,
              status,
              created_at,
              updated_at
            ) VALUES (
              #{id},
              #{businessType},
              #{businessId},
              #{processInstanceId},
              #{processDefinitionId},
              #{startUserId},
              #{status},
              CURRENT_TIMESTAMP,
              CURRENT_TIMESTAMP
            )
            """)
    int insertLink(BusinessProcessLinkRecord record);

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              business_id AS businessId,
              process_instance_id AS processInstanceId,
              process_definition_id AS processDefinitionId,
              start_user_id AS startUserId,
              status
            FROM wf_business_process_link
            WHERE process_instance_id = #{processInstanceId}
            ORDER BY updated_at DESC, created_at DESC
            LIMIT 1
            """)
    BusinessProcessLinkRecord selectByProcessInstanceId(@Param("processInstanceId") String processInstanceId);

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              business_id AS businessId,
              process_instance_id AS processInstanceId,
              process_definition_id AS processDefinitionId,
              start_user_id AS startUserId,
              status
            FROM wf_business_process_link
            WHERE business_type = #{businessType}
              AND business_id = #{businessId}
            ORDER BY updated_at DESC, created_at DESC
            LIMIT 1
            """)
    BusinessProcessLinkRecord selectByBusiness(
            @Param("businessType") String businessType,
            @Param("businessId") String businessId
    );

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              business_id AS businessId,
              process_instance_id AS processInstanceId,
              process_definition_id AS processDefinitionId,
              start_user_id AS startUserId,
              status
            FROM wf_business_process_link
            ORDER BY updated_at DESC, created_at DESC
            """)
    List<BusinessProcessLinkRecord> selectAll();

    @Select("""
            SELECT
              id,
              business_type AS businessType,
              business_id AS businessId,
              process_instance_id AS processInstanceId,
              process_definition_id AS processDefinitionId,
              start_user_id AS startUserId,
              status
            FROM wf_business_process_link
            WHERE start_user_id = #{startUserId}
            ORDER BY updated_at DESC, created_at DESC
            """)
    List<BusinessProcessLinkRecord> selectByStartUser(@Param("startUserId") String startUserId);

    @Update("""
            UPDATE wf_business_process_link
            SET status = #{status},
                updated_at = CURRENT_TIMESTAMP
            WHERE process_instance_id = #{processInstanceId}
            """)
    int updateStatusByProcessInstanceId(
            @Param("processInstanceId") String processInstanceId,
            @Param("status") String status
    );
}
