package com.westflow.processbinding.mapper;

import com.westflow.processbinding.model.BusinessProcessLinkRecord;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;

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
}
