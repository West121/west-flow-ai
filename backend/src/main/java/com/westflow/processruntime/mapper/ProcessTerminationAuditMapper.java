package com.westflow.processruntime.mapper;

import com.westflow.processruntime.termination.model.ProcessTerminationAuditRecord;
import java.util.List;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 终止审计记录映射。
 */
@Mapper
public interface ProcessTerminationAuditMapper {

    @Insert("""
            INSERT INTO wf_process_termination_audit (
              id,
              root_instance_id,
              target_instance_id,
              parent_instance_id,
              target_kind,
              terminate_scope,
              propagation_policy,
              event_type,
              result_status,
              reason,
              operator_user_id,
              detail_json,
              created_at,
              finished_at
            ) VALUES (
              #{auditId},
              #{rootInstanceId},
              #{targetInstanceId},
              #{parentInstanceId},
              #{targetKind},
              #{terminateScope},
              #{propagationPolicy},
              #{eventType},
              #{resultStatus},
              #{reason},
              #{operatorUserId},
              #{detailJson},
              #{createdAt},
              #{finishedAt}
            )
            """)
    int insert(ProcessTerminationAuditRecord record);

    @Select("""
            SELECT
              id AS auditId,
              root_instance_id AS rootInstanceId,
              target_instance_id AS targetInstanceId,
              parent_instance_id AS parentInstanceId,
              target_kind AS targetKind,
              terminate_scope AS terminateScope,
              propagation_policy AS propagationPolicy,
              event_type AS eventType,
              result_status AS resultStatus,
              reason AS reason,
              operator_user_id AS operatorUserId,
              detail_json AS detailJson,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_process_termination_audit
            WHERE root_instance_id = #{rootInstanceId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ProcessTerminationAuditRecord> selectByRootInstanceId(@Param("rootInstanceId") String rootInstanceId);

    @Select("""
            SELECT
              id AS auditId,
              root_instance_id AS rootInstanceId,
              target_instance_id AS targetInstanceId,
              parent_instance_id AS parentInstanceId,
              target_kind AS targetKind,
              terminate_scope AS terminateScope,
              propagation_policy AS propagationPolicy,
              event_type AS eventType,
              result_status AS resultStatus,
              reason AS reason,
              operator_user_id AS operatorUserId,
              detail_json AS detailJson,
              created_at AS createdAt,
              finished_at AS finishedAt
            FROM wf_process_termination_audit
            WHERE target_instance_id = #{targetInstanceId}
            ORDER BY created_at ASC, id ASC
            """)
    List<ProcessTerminationAuditRecord> selectByTargetInstanceId(@Param("targetInstanceId") String targetInstanceId);
}
