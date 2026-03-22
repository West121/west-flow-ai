package com.westflow.workflowadmin.mapper;

import com.westflow.workflowadmin.model.WorkflowOperationLogRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 流程操作日志表映射。
 */
@Mapper
public interface WorkflowOperationLogMapper {

    @Insert("""
            INSERT INTO wf_workflow_operation_log (
              id,
              process_instance_id,
              process_definition_id,
              flowable_definition_id,
              business_type,
              business_id,
              task_id,
              node_id,
              action_type,
              action_name,
              action_category,
              operator_user_id,
              target_user_id,
              source_task_id,
              target_task_id,
              comment_text,
              detail_json,
              created_at
            ) VALUES (
              #{logId},
              #{processInstanceId},
              #{processDefinitionId},
              #{flowableDefinitionId},
              #{businessType},
              #{businessId},
              #{taskId},
              #{nodeId},
              #{actionType},
              #{actionName},
              #{actionCategory},
              #{operatorUserId},
              #{targetUserId},
              #{sourceTaskId},
              #{targetTaskId},
              #{commentText},
              #{detailJson},
              #{createdAt}
            )
            """)
    int insert(WorkflowOperationLogRecord record);

    @Select("""
            SELECT
              id AS logId,
              process_instance_id AS processInstanceId,
              process_definition_id AS processDefinitionId,
              flowable_definition_id AS flowableDefinitionId,
              business_type AS businessType,
              business_id AS businessId,
              task_id AS taskId,
              node_id AS nodeId,
              action_type AS actionType,
              action_name AS actionName,
              action_category AS actionCategory,
              operator_user_id AS operatorUserId,
              target_user_id AS targetUserId,
              source_task_id AS sourceTaskId,
              target_task_id AS targetTaskId,
              comment_text AS commentText,
              detail_json AS detailJson,
              created_at AS createdAt
            FROM wf_workflow_operation_log
            ORDER BY created_at DESC, id DESC
            """)
    List<WorkflowOperationLogRecord> selectAll();

    @Select("""
            SELECT
              id AS logId,
              process_instance_id AS processInstanceId,
              process_definition_id AS processDefinitionId,
              flowable_definition_id AS flowableDefinitionId,
              business_type AS businessType,
              business_id AS businessId,
              task_id AS taskId,
              node_id AS nodeId,
              action_type AS actionType,
              action_name AS actionName,
              action_category AS actionCategory,
              operator_user_id AS operatorUserId,
              target_user_id AS targetUserId,
              source_task_id AS sourceTaskId,
              target_task_id AS targetTaskId,
              comment_text AS commentText,
              detail_json AS detailJson,
              created_at AS createdAt
            FROM wf_workflow_operation_log
            WHERE id = #{logId}
            """)
    WorkflowOperationLogRecord selectById(@Param("logId") String logId);
}
