package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.task.api.Task;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeCountersignVoteService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final JdbcTemplate jdbcTemplate;
    private final RuntimeCountersignSupportService countersignSupportService;

    public Map<String, Object> prepareCompletionVariables(String processDefinitionId, Task task, String action) {
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null || task == null) {
            return Map.of();
        }
        CountersignNodeMetadata metadata =
                countersignSupportService.resolveCountersignMetadata(model, task.getProcessInstanceId(), task.getTaskDefinitionKey());
        if (metadata == null) {
            return Map.of();
        }
        if ("OR_SIGN".equals(metadata.approvalMode())) {
            if (!"APPROVE".equals(action)) {
                return Map.of();
            }
            return Map.of(countersignSupportService.countersignDecisionVariable(metadata.nodeId()), "APPROVED");
        }
        if (!"VOTE".equals(metadata.approvalMode())) {
            return Map.of();
        }
        TaskGroupMemberRecord member = findMemberByTaskId(task.getId()).orElse(null);
        if (member == null || member.voteWeight() == null || member.voteWeight() <= 0) {
            return Map.of();
        }
        VoteSnapshotRecord snapshot = findVoteSnapshot(task.getProcessInstanceId(), metadata.nodeId())
                .orElseGet(() -> createVoteSnapshot(task.getProcessInstanceId(), metadata));
        int approvedWeight = snapshot.approvedWeight();
        int rejectedWeight = snapshot.rejectedWeight();
        if ("APPROVE".equals(action)) {
            approvedWeight += member.voteWeight();
        } else if ("REJECT".equals(action)) {
            rejectedWeight += member.voteWeight();
        } else {
            return Map.of();
        }
        String decision = null;
        if (approvedWeight * 100 >= snapshot.totalWeight() * snapshot.thresholdPercent()) {
            decision = "APPROVED";
        } else if (rejectedWeight * 100 >= snapshot.totalWeight() * snapshot.thresholdPercent()) {
            decision = "REJECTED";
        }
        upsertVoteSnapshot(snapshot.id(), approvedWeight, rejectedWeight, decision);
        if (decision == null) {
            return Map.of();
        }
        return Map.of(countersignSupportService.countersignDecisionVariable(metadata.nodeId()), decision);
    }

    Optional<VoteSnapshotRecord> findVoteSnapshot(String processInstanceId, String nodeId) {
        return jdbcTemplate.query(
                """
                SELECT id, process_instance_id, node_id, threshold_percent, total_weight, approved_weight, rejected_weight, decision_status
                FROM wf_task_vote_snapshot
                WHERE process_instance_id = ?
                  AND node_id = ?
                """,
                countersignSupportService::mapVoteSnapshot,
                processInstanceId,
                nodeId
        ).stream().findFirst();
    }

    VoteSnapshotRecord createVoteSnapshot(String processInstanceId, CountersignNodeMetadata metadata) {
        VoteSnapshotRecord existing = findVoteSnapshot(processInstanceId, metadata.nodeId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        int totalWeight = metadata.userIds().stream()
                .mapToInt(userId -> countersignSupportService.resolveVoteWeight(metadata, userId))
                .sum();
        VoteSnapshotRecord snapshot = new VoteSnapshotRecord(
                "tvs_" + UUID.randomUUID().toString().replace("-", ""),
                processInstanceId,
                metadata.nodeId(),
                metadata.voteThresholdPercent() == null ? 0 : metadata.voteThresholdPercent(),
                totalWeight,
                0,
                0,
                null
        );
        jdbcTemplate.update(
                """
                INSERT INTO wf_task_vote_snapshot (
                  id,
                  process_instance_id,
                  node_id,
                  threshold_percent,
                  total_weight,
                  approved_weight,
                  rejected_weight,
                  decision_status,
                  decided_at,
                  created_at,
                  updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                snapshot.id(),
                snapshot.processInstanceId(),
                snapshot.nodeId(),
                snapshot.thresholdPercent(),
                snapshot.totalWeight(),
                snapshot.approvedWeight(),
                snapshot.rejectedWeight(),
                snapshot.decisionStatus(),
                null
        );
        return snapshot;
    }

    void deleteVoteSnapshot(String processInstanceId, String nodeId) {
        jdbcTemplate.update(
                """
                DELETE FROM wf_task_vote_snapshot
                WHERE process_instance_id = ?
                  AND node_id = ?
                """,
                processInstanceId,
                nodeId
        );
    }

    String countersignDecision(String processInstanceId, String nodeId) {
        try {
            Object runtimeValue = flowableEngineFacade.runtimeService()
                    .getVariable(processInstanceId, countersignSupportService.countersignDecisionVariable(nodeId));
            if (runtimeValue != null) {
                return String.valueOf(runtimeValue);
            }
        } catch (FlowableObjectNotFoundException ignored) {
            // 实例已结束时回退到历史变量查询。
        }
        org.flowable.variable.api.history.HistoricVariableInstance historicValue = flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(countersignSupportService.countersignDecisionVariable(nodeId))
                .singleResult();
        if (historicValue != null && historicValue.getValue() != null) {
            return String.valueOf(historicValue.getValue());
        }
        return null;
    }

    private Optional<TaskGroupMemberRecord> findMemberByTaskId(String taskId) {
        return jdbcTemplate.query(
                """
                SELECT id, task_group_id, process_instance_id, node_id, task_id, assignee_user_id, sequence_no, vote_weight, member_status
                FROM wf_task_group_member
                WHERE task_id = ?
                """,
                countersignSupportService::mapTaskGroupMember,
                taskId
        ).stream().findFirst();
    }

    private void upsertVoteSnapshot(String snapshotId, int approvedWeight, int rejectedWeight, String decision) {
        jdbcTemplate.update(
                """
                UPDATE wf_task_vote_snapshot
                SET approved_weight = ?,
                    rejected_weight = ?,
                    decision_status = ?,
                    decided_at = CASE WHEN ? IS NULL THEN decided_at ELSE CURRENT_TIMESTAMP END,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                approvedWeight,
                rejectedWeight,
                decision,
                decision,
                snapshotId
        );
    }
}
