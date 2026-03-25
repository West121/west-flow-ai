package com.westflow.processruntime.service;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.CountersignTaskGroupMemberResponse;
import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
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
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.common.engine.api.FlowableObjectNotFoundException;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 维护会签节点的任务组与成员快照，便于后续详情页和票签能力复用。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableCountersignService {

    private final FlowableEngineFacade flowableEngineFacade;
    private final JdbcTemplate jdbcTemplate;

    /**
     * 流程实例启动后，为当前激活的会签节点建立任务组快照。
     */
    public void initializeTaskGroups(String processDefinitionId, String processInstanceId) {
        syncTaskGroups(processDefinitionId, processInstanceId, null);
    }

    /**
     * 任务完成后刷新会签分组状态，把下一个活动任务绑定到成员快照上。
     */
    public void syncAfterTaskCompleted(String processDefinitionId, String processInstanceId, String completedTaskId) {
        syncTaskGroups(processDefinitionId, processInstanceId, completedTaskId);
    }

    /**
     * 在任务完成前计算会签决议变量，确保 Flowable 在本次完成动作里就能命中完成条件。
     */
    public Map<String, Object> prepareCompletionVariables(
            String processDefinitionId,
            Task task,
            String action
    ) {
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(processDefinitionId);
        if (model == null || task == null) {
            return Map.of();
        }
        CountersignNodeMetadata metadata = resolveCountersignMetadata(model, task.getProcessInstanceId(), task.getTaskDefinitionKey());
        if (metadata == null) {
            return Map.of();
        }
        if ("OR_SIGN".equals(metadata.approvalMode())) {
            if (!"APPROVE".equals(action)) {
                return Map.of();
            }
            return Map.of(countersignDecisionVariable(metadata.nodeId()), "APPROVED");
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
        return Map.of(countersignDecisionVariable(metadata.nodeId()), decision);
    }

    /**
     * 查询流程实例下的会签任务组快照。
     */
    public List<CountersignTaskGroupResponse> queryTaskGroups(String processInstanceId) {
        return listGroups(processInstanceId).stream()
                .map(group -> {
                    List<TaskGroupMemberRecord> members = listMembers(group.id());
                    VoteSnapshotRecord voteSnapshot = "VOTE".equals(group.approvalMode())
                            ? findVoteSnapshot(processInstanceId, group.nodeId()).orElse(null)
                            : null;
                    int totalCount = members.size();
                    int completedCount = (int) members.stream()
                            .filter(member -> "COMPLETED".equals(member.memberStatus()))
                            .count();
                    int activeCount = (int) members.stream()
                            .filter(member -> "ACTIVE".equals(member.memberStatus()))
                            .count();
                    int waitingCount = (int) members.stream()
                            .filter(member -> "WAITING".equals(member.memberStatus()))
                            .count();
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
            CountersignNodeMetadata metadata = resolveCountersignMetadata(model, processInstanceId, entry.getKey());
            if (metadata == null) {
                continue;
            }
            TaskGroupRecord group = findRunningGroup(processInstanceId, entry.getKey())
                    .orElseGet(() -> createTaskGroup(processInstanceId, metadata, entry.getValue()));
            bindActiveTasks(group, metadata, entry.getValue());
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

    private TaskGroupRecord createTaskGroup(
            String processInstanceId,
            CountersignNodeMetadata metadata,
            List<Task> activeTasks
    ) {
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
            Integer voteWeight = resolveVoteWeight(metadata, userId);
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
            createVoteSnapshot(processInstanceId, metadata);
        }
        return new TaskGroupRecord(groupId, processInstanceId, metadata.nodeId(), metadata.nodeName(), metadata.approvalMode(), "RUNNING");
    }

    private void bindActiveTasks(TaskGroupRecord group, CountersignNodeMetadata metadata, List<Task> activeTasks) {
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
        String decision = countersignDecision(processInstanceId, group.nodeId());
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
                this::mapTaskGroup,
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
                this::mapTaskGroup,
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
                this::mapTaskGroup,
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
                this::mapTaskGroupMember,
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

    private CountersignNodeMetadata resolveCountersignMetadata(BpmnModel model, String processInstanceId, String nodeId) {
        BaseElement element = model.getFlowElement(nodeId);
        if (element == null) {
            return null;
        }
        String approvalMode = extensionValue(element, "approvalMode");
        if (!"SEQUENTIAL".equals(approvalMode)
                && !"PARALLEL".equals(approvalMode)
                && !"OR_SIGN".equals(approvalMode)
                && !"VOTE".equals(approvalMode)) {
            return null;
        }
        List<String> userIds = commaSeparatedValue(extensionValue(element, "userIds"));
        if (userIds.size() < 2 && processInstanceId != null && !processInstanceId.isBlank()) {
            userIds = runtimeCountersignAssignees(processInstanceId, nodeId);
        }
        if (userIds.size() < 2) {
            return null;
        }
        return new CountersignNodeMetadata(
                nodeId,
                Optional.ofNullable(extensionValue(element, "description")).filter(value -> !value.isBlank())
                        .orElseGet(() -> {
                            org.flowable.bpmn.model.FlowElement flowElement = model.getFlowElement(nodeId);
                            return flowElement == null ? nodeId : flowElement.getName();
                        }),
                approvalMode,
                extensionValue(element, "reapprovePolicy"),
                userIds,
                "true".equalsIgnoreCase(extensionValue(element, "autoFinishRemaining")),
                integerValue(extensionValue(element, "voteThresholdPercent")),
                parseVoteWeights(extensionValue(element, "voteWeights"))
        );
    }

    private List<String> runtimeCountersignAssignees(String processInstanceId, String nodeId) {
        Object runtimeValue = flowableEngineFacade.runtimeService()
                .getVariable(processInstanceId, countersignCollectionVariable(nodeId));
        if (runtimeValue instanceof List<?> values) {
            return values.stream()
                    .map(String::valueOf)
                    .filter(value -> value != null && !value.isBlank())
                    .distinct()
                    .toList();
        }
        if (runtimeValue instanceof String value && !value.isBlank()) {
            return commaSeparatedValue(value);
        }
        return List.of();
    }

    private Optional<TaskGroupMemberRecord> findMemberByTaskId(String taskId) {
        return jdbcTemplate.query(
                """
                SELECT id, task_group_id, process_instance_id, node_id, task_id, assignee_user_id, sequence_no, vote_weight, member_status
                FROM wf_task_group_member
                WHERE task_id = ?
                """,
                this::mapTaskGroupMember,
                taskId
        ).stream().findFirst();
    }

    private Optional<VoteSnapshotRecord> findVoteSnapshot(String processInstanceId, String nodeId) {
        return jdbcTemplate.query(
                """
                SELECT id, process_instance_id, node_id, threshold_percent, total_weight, approved_weight, rejected_weight, decision_status
                FROM wf_task_vote_snapshot
                WHERE process_instance_id = ?
                  AND node_id = ?
                """,
                this::mapVoteSnapshot,
                processInstanceId,
                nodeId
        ).stream().findFirst();
    }

    private VoteSnapshotRecord createVoteSnapshot(String processInstanceId, CountersignNodeMetadata metadata) {
        VoteSnapshotRecord existing = findVoteSnapshot(processInstanceId, metadata.nodeId()).orElse(null);
        if (existing != null) {
            return existing;
        }
        int totalWeight = metadata.userIds().stream()
                .mapToInt(userId -> resolveVoteWeight(metadata, userId))
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

    private String countersignDecision(String processInstanceId, String nodeId) {
        try {
            Object runtimeValue = flowableEngineFacade.runtimeService()
                    .getVariable(processInstanceId, countersignDecisionVariable(nodeId));
            if (runtimeValue != null) {
                return String.valueOf(runtimeValue);
            }
        } catch (FlowableObjectNotFoundException ignored) {
            // 实例已结束时回退到历史变量查询。
        }
        org.flowable.variable.api.history.HistoricVariableInstance historicValue = flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .processInstanceId(processInstanceId)
                .variableName(countersignDecisionVariable(nodeId))
                .singleResult();
        if (historicValue != null && historicValue.getValue() != null) {
            return String.valueOf(historicValue.getValue());
        }
        return null;
    }

    private String countersignDecisionVariable(String nodeId) {
        return "wfCountersignDecision_" + nodeId;
    }

    private String countersignCollectionVariable(String nodeId) {
        return "wfCountersignAssignees_" + nodeId;
    }

    private String countersignElementVariable(String nodeId) {
        return "wfCountersignAssignee_" + nodeId;
    }

    private int resolveVoteWeight(CountersignNodeMetadata metadata, String userId) {
        if (!"VOTE".equals(metadata.approvalMode())) {
            return 0;
        }
        return Optional.ofNullable(metadata.voteWeights().get(userId)).orElse(1);
    }

    private String extensionValue(BaseElement element, String name) {
        if (element == null || element.getAttributes() == null || element.getAttributes().isEmpty()) {
            return null;
        }
        List<org.flowable.bpmn.model.ExtensionAttribute> attrs = element.getAttributes().get(name);
        if (attrs == null || attrs.isEmpty()) {
            return null;
        }
        String value = attrs.get(0).getValue();
        return value == null || value.isBlank() ? null : value;
    }

    private List<String> commaSeparatedValue(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private Map<String, Integer> parseVoteWeights(String value) {
        if (value == null || value.isBlank()) {
            return Map.of();
        }
        Map<String, Integer> weights = new LinkedHashMap<>();
        for (String item : value.split(",")) {
            String trimmed = item.trim();
            if (trimmed.isBlank()) {
                continue;
            }
            String[] pair = trimmed.split(":");
            if (pair.length != 2) {
                continue;
            }
            Integer weight = integerValue(pair[1]);
            if (weight == null) {
                continue;
            }
            weights.put(pair[0].trim(), weight);
        }
        return weights;
    }

    private Integer integerValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Integer.parseInt(value.trim());
    }

    private TaskGroupRecord mapTaskGroup(ResultSet resultSet, int rowNum) throws SQLException {
        return new TaskGroupRecord(
                resultSet.getString("id"),
                resultSet.getString("process_instance_id"),
                resultSet.getString("node_id"),
                resultSet.getString("node_name"),
                resultSet.getString("approval_mode"),
                resultSet.getString("group_status")
        );
    }

    private TaskGroupMemberRecord mapTaskGroupMember(ResultSet resultSet, int rowNum) throws SQLException {
        return new TaskGroupMemberRecord(
                resultSet.getString("id"),
                resultSet.getString("task_group_id"),
                resultSet.getString("process_instance_id"),
                resultSet.getString("node_id"),
                resultSet.getString("task_id"),
                resultSet.getString("assignee_user_id"),
                resultSet.getInt("sequence_no"),
                (Integer) resultSet.getObject("vote_weight"),
                resultSet.getString("member_status")
        );
    }

    private VoteSnapshotRecord mapVoteSnapshot(ResultSet resultSet, int rowNum) throws SQLException {
        return new VoteSnapshotRecord(
                resultSet.getString("id"),
                resultSet.getString("process_instance_id"),
                resultSet.getString("node_id"),
                resultSet.getInt("threshold_percent"),
                resultSet.getInt("total_weight"),
                resultSet.getInt("approved_weight"),
                resultSet.getInt("rejected_weight"),
                resultSet.getString("decision_status")
        );
    }

    /**
     * 会签节点的 BPMN 元数据快照。
     */
    private record CountersignNodeMetadata(
            String nodeId,
            String nodeName,
            String approvalMode,
            String reapprovePolicy,
            List<String> userIds,
            boolean autoFinishRemaining,
            Integer voteThresholdPercent,
            Map<String, Integer> voteWeights
    ) {
    }

    /**
     * 会签任务组快照。
     */
    private record TaskGroupRecord(
            String id,
            String processInstanceId,
            String nodeId,
            String nodeName,
            String approvalMode,
            String groupStatus
    ) {
    }

    /**
     * 会签成员快照。
     */
    private record TaskGroupMemberRecord(
            String id,
            String taskGroupId,
            String processInstanceId,
            String nodeId,
            String taskId,
            String assigneeUserId,
            int sequenceNo,
            Integer voteWeight,
            String memberStatus
    ) {
    }

    /**
     * 票签累计快照。
     */
    private record VoteSnapshotRecord(
            String id,
            String processInstanceId,
            String nodeId,
            int thresholdPercent,
            int totalWeight,
            int approvedWeight,
            int rejectedWeight,
            String decisionStatus
    ) {
    }
}
