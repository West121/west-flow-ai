package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.CountersignTaskGroupMemberResponse;
import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeCountersignSnapshotService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final JdbcTemplate jdbcTemplate;
    private final RuntimeCountersignSupportService countersignSupportService;
    private final RuntimeCountersignVoteService runtimeCountersignVoteService;

    public void initializeTaskGroups(String processDefinitionId, String processInstanceId) {
        syncTaskGroups(processDefinitionId, processInstanceId, null);
    }

    public void syncAfterTaskCompleted(String processDefinitionId, String processInstanceId, String completedTaskId) {
        syncTaskGroups(processDefinitionId, processInstanceId, completedTaskId);
    }

    public List<CountersignTaskGroupResponse> queryTaskGroups(String processInstanceId) {
        return listGroups(processInstanceId).stream()
                .map(group -> {
                    List<TaskGroupMemberRecord> members = listMembers(group.id());
                    VoteSnapshotRecord voteSnapshot = "VOTE".equals(group.approvalMode())
                            ? runtimeCountersignVoteService.findVoteSnapshot(processInstanceId, group.nodeId()).orElse(null)
                            : null;
                    int totalCount = members.size();
                    int completedCount = (int) members.stream().filter(member -> "COMPLETED".equals(member.memberStatus())).count();
                    int activeCount = (int) members.stream().filter(member -> "ACTIVE".equals(member.memberStatus())).count();
                    int waitingCount = (int) members.stream().filter(member -> "WAITING".equals(member.memberStatus())).count();
                    return new CountersignTaskGroupResponse(
                            group.id(),
                            group.processInstanceId(),
                            group.nodeId(),
                            group.nodeName(),
                            group.approvalMode(),
                            group.groupStatus(),
                            totalCount,
                            completedCount,
                            activeCount,
                            waitingCount,
                            voteSnapshot == null ? null : voteSnapshot.thresholdPercent(),
                            voteSnapshot == null ? null : voteSnapshot.approvedWeight(),
                            voteSnapshot == null ? null : voteSnapshot.rejectedWeight(),
                            voteSnapshot == null ? null : voteSnapshot.decisionStatus(),
                            members.stream()
                                    .map(member -> new CountersignTaskGroupMemberResponse(
                                            member.id(),
                                            member.taskId(),
                                            member.assigneeUserId(),
                                            member.sequenceNo(),
                                            member.voteWeight(),
                                            member.memberStatus()
                                    ))
                                    .toList()
                    );
                })
                .toList();
    }

    public void rebuildTaskGroupsForNode(String processDefinitionId, String processInstanceId, String nodeId) {
        deleteRunningGroups(processInstanceId, nodeId);
        runtimeCountersignVoteService.deleteVoteSnapshot(processInstanceId, nodeId);
        syncTaskGroups(processDefinitionId, processInstanceId, null);
    }

    private void syncTaskGroups(String processDefinitionId, String processInstanceId, String completedTaskId) {
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null) {
            return;
        }
        if (completedTaskId != null && !completedTaskId.isBlank()) {
            markMemberCompleted(completedTaskId);
        }

        Map<String, List<Task>> activeTasksByNode = flowableEngineFacade.taskService()
                .createTaskQuery()
                .processInstanceId(processInstanceId)
                .active()
                .orderByTaskCreateTime()
                .asc()
                .list()
                .stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        Task::getTaskDefinitionKey,
                        LinkedHashMap::new,
                        java.util.stream.Collectors.toList()
                ));

        for (Map.Entry<String, List<Task>> entry : activeTasksByNode.entrySet()) {
            CountersignNodeMetadata metadata = countersignSupportService.resolveCountersignMetadata(model, processInstanceId, entry.getKey());
            if (metadata == null) {
                continue;
            }
            TaskGroupRecord group = findRunningGroup(processInstanceId, entry.getKey())
                    .orElseGet(() -> createTaskGroup(processInstanceId, metadata, entry.getValue()));
            bindActiveTasks(group, entry.getValue());
        }

        listRunningGroups(processInstanceId).forEach(group -> {
            if (activeTasksByNode.containsKey(group.nodeId())) {
                updateGroupStatus(group.id(), "RUNNING");
                return;
            }
            if (shouldAutoFinishRemainingMembers(processInstanceId, group)) {
                markAutoFinishedMembers(processInstanceId, group);
                updateGroupStatus(group.id(), "COMPLETED");
                return;
            }
            if (hasPendingMembers(group.id())) {
                updateGroupStatus(group.id(), "RUNNING");
                return;
            }
            updateGroupStatus(group.id(), "COMPLETED");
        });
    }

    private TaskGroupRecord createTaskGroup(String processInstanceId, CountersignNodeMetadata metadata, List<Task> activeTasks) {
        String groupId = "tg_" + UUID.randomUUID().toString().replace("-", "");
        jdbcTemplate.update(
                """
                INSERT INTO wf_task_group (
                  id,
                  process_instance_id,
                  node_id,
                  node_name,
                  approval_mode,
                  reapprove_policy,
                  group_status,
                  created_at,
                  updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """,
                groupId,
                processInstanceId,
                metadata.nodeId(),
                metadata.nodeName(),
                metadata.approvalMode(),
                metadata.reapprovePolicy(),
                "RUNNING"
        );

        Map<String, Task> activeTaskByAssignee = new LinkedHashMap<>();
        activeTasks.stream()
                .filter(task -> task.getAssignee() != null && !task.getAssignee().isBlank())
                .forEach(task -> activeTaskByAssignee.put(task.getAssignee(), task));

        for (int index = 0; index < metadata.userIds().size(); index++) {
            String userId = metadata.userIds().get(index);
            Task activeTask = activeTaskByAssignee.get(userId);
            Integer voteWeight = countersignSupportService.resolveVoteWeight(metadata, userId);
            jdbcTemplate.update(
                    """
                    INSERT INTO wf_task_group_member (
                      id,
                      task_group_id,
                      process_instance_id,
                      node_id,
                      task_id,
                      assignee_user_id,
                      sequence_no,
                      vote_weight,
                      member_status,
                      completed_at,
                      created_at,
                      updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """,
                    "tgm_" + UUID.randomUUID().toString().replace("-", ""),
                    groupId,
                    processInstanceId,
                    metadata.nodeId(),
                    activeTask == null ? null : activeTask.getId(),
                    userId,
                    index + 1,
                    voteWeight,
                    activeTask == null ? "WAITING" : "ACTIVE",
                    null
            );
        }
        if ("VOTE".equals(metadata.approvalMode())) {
            runtimeCountersignVoteService.createVoteSnapshot(processInstanceId, metadata);
        }
        return new TaskGroupRecord(groupId, processInstanceId, metadata.nodeId(), metadata.nodeName(), metadata.approvalMode(), "RUNNING");
    }

    private void bindActiveTasks(TaskGroupRecord group, List<Task> activeTasks) {
        List<TaskGroupMemberRecord> members = listMembers(group.id());
        Map<String, TaskGroupMemberRecord> membersByTaskId = new LinkedHashMap<>();
        members.stream()
                .filter(member -> member.taskId() != null && !member.taskId().isBlank())
                .forEach(member -> membersByTaskId.put(member.taskId(), member));

        List<TaskGroupMemberRecord> waitingMembers = new ArrayList<>(members.stream()
                .filter(member -> "WAITING".equals(member.memberStatus()))
                .sorted(Comparator.comparingInt(TaskGroupMemberRecord::sequenceNo))
                .toList());

        for (Task activeTask : activeTasks) {
            if (membersByTaskId.containsKey(activeTask.getId())) {
                continue;
            }
            TaskGroupMemberRecord targetMember = waitingMembers.stream()
                    .filter(member -> member.assigneeUserId().equals(activeTask.getAssignee()))
                    .findFirst()
                    .orElseGet(() -> waitingMembers.isEmpty() ? null : waitingMembers.get(0));
            if (targetMember == null) {
                continue;
            }
            jdbcTemplate.update(
                    """
                    UPDATE wf_task_group_member
                    SET task_id = ?,
                        member_status = 'ACTIVE',
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    activeTask.getId(),
                    targetMember.id()
            );
            waitingMembers.removeIf(member -> member.id().equals(targetMember.id()));
        }

        updateGroupStatus(group.id(), "RUNNING");
    }

    private void markMemberCompleted(String completedTaskId) {
        jdbcTemplate.update(
                """
                UPDATE wf_task_group_member
                SET member_status = 'COMPLETED',
                    completed_at = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE task_id = ?
                """,
                Timestamp.from(Instant.now()),
                completedTaskId
        );
    }

    private boolean hasPendingMembers(String groupId) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(1)
                FROM wf_task_group_member
                WHERE task_group_id = ?
                  AND member_status IN ('WAITING', 'ACTIVE')
                """,
                Integer.class,
                groupId
        );
        return count != null && count > 0;
    }

    private boolean shouldAutoFinishRemainingMembers(String processInstanceId, TaskGroupRecord group) {
        if (!"OR_SIGN".equals(group.approvalMode()) && !"VOTE".equals(group.approvalMode())) {
            return false;
        }
        String decision = runtimeCountersignVoteService.countersignDecision(processInstanceId, group.nodeId());
        return decision != null && !decision.isBlank();
    }

    private void markAutoFinishedMembers(String processInstanceId, TaskGroupRecord group) {
        List<TaskGroupMemberRecord> members = listMembers(group.id());
        Map<String, String> deleteReasonByTaskId = flowableEngineFacade.historyService()
                .createHistoricTaskInstanceQuery()
                .processInstanceId(processInstanceId)
                .taskDefinitionKey(group.nodeId())
                .list()
                .stream()
                .filter(task -> task.getDeleteReason() != null && !task.getDeleteReason().isBlank())
                .collect(java.util.stream.Collectors.toMap(
                        HistoricTaskInstance::getId,
                        HistoricTaskInstance::getDeleteReason,
                        (left, right) -> left,
                        LinkedHashMap::new
                ));
        for (TaskGroupMemberRecord member : members) {
            if ("COMPLETED".equals(member.memberStatus())) {
                continue;
            }
            if (member.taskId() == null || !deleteReasonByTaskId.containsKey(member.taskId())) {
                continue;
            }
            jdbcTemplate.update(
                    """
                    UPDATE wf_task_group_member
                    SET member_status = 'AUTO_FINISHED',
                        completed_at = ?,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """,
                    Timestamp.from(Instant.now()),
                    member.id()
            );
        }
    }

    private Optional<TaskGroupRecord> findRunningGroup(String processInstanceId, String nodeId) {
        List<TaskGroupRecord> records = jdbcTemplate.query(
                """
                SELECT id, process_instance_id, node_id, node_name, approval_mode, group_status
                FROM wf_task_group
                WHERE process_instance_id = ?
                  AND node_id = ?
                  AND group_status = 'RUNNING'
                ORDER BY created_at DESC
                """,
                countersignSupportService::mapTaskGroup,
                processInstanceId,
                nodeId
        );
        return records.stream().findFirst();
    }

    private List<TaskGroupRecord> listRunningGroups(String processInstanceId) {
        return jdbcTemplate.query(
                """
                SELECT id, process_instance_id, node_id, node_name, approval_mode, group_status
                FROM wf_task_group
                WHERE process_instance_id = ?
                  AND group_status = 'RUNNING'
                ORDER BY created_at ASC
                """,
                countersignSupportService::mapTaskGroup,
                processInstanceId
        );
    }

    private List<TaskGroupRecord> listGroups(String processInstanceId) {
        return jdbcTemplate.query(
                """
                SELECT id, process_instance_id, node_id, node_name, approval_mode, group_status
                FROM wf_task_group
                WHERE process_instance_id = ?
                ORDER BY created_at ASC
                """,
                countersignSupportService::mapTaskGroup,
                processInstanceId
        );
    }

    private List<TaskGroupMemberRecord> listMembers(String groupId) {
        return jdbcTemplate.query(
                """
                SELECT id, task_group_id, process_instance_id, node_id, task_id, assignee_user_id, sequence_no, vote_weight, member_status
                FROM wf_task_group_member
                WHERE task_group_id = ?
                ORDER BY sequence_no ASC
                """,
                countersignSupportService::mapTaskGroupMember,
                groupId
        );
    }

    private void updateGroupStatus(String groupId, String status) {
        jdbcTemplate.update(
                """
                UPDATE wf_task_group
                SET group_status = ?,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                status,
                groupId
        );
    }

    private void deleteRunningGroups(String processInstanceId, String nodeId) {
        List<String> groupIds = jdbcTemplate.query(
                """
                SELECT id
                FROM wf_task_group
                WHERE process_instance_id = ?
                  AND node_id = ?
                  AND group_status = 'RUNNING'
                ORDER BY created_at DESC
                """,
                (resultSet, rowNum) -> resultSet.getString("id"),
                processInstanceId,
                nodeId
        );
        for (String groupId : groupIds) {
            jdbcTemplate.update("DELETE FROM wf_task_group_member WHERE task_group_id = ?", groupId);
            jdbcTemplate.update("DELETE FROM wf_task_group WHERE id = ?", groupId);
        }
    }
}
