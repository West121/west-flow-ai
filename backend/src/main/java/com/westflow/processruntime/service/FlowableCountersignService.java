package com.westflow.processruntime.service;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.CountersignTaskGroupMemberResponse;
import com.westflow.processruntime.api.CountersignTaskGroupResponse;
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
import org.flowable.task.api.Task;
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
     * 查询流程实例下的会签任务组快照。
     */
    public List<CountersignTaskGroupResponse> queryTaskGroups(String processInstanceId) {
        return listGroups(processInstanceId).stream()
                .map(group -> {
                    List<TaskGroupMemberRecord> members = listMembers(group.id());
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
                            members.stream()
                                    .map(member -> new CountersignTaskGroupMemberResponse(
                                            member.id(),
                                            member.taskId(),
                                            member.assigneeUserId(),
                                            member.sequenceNo(),
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
            CountersignNodeMetadata metadata = resolveCountersignMetadata(model, entry.getKey());
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
                    null,
                    activeTask == null ? "WAITING" : "ACTIVE",
                    null
            );
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
                SELECT id, task_group_id, process_instance_id, node_id, task_id, assignee_user_id, sequence_no, member_status
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

    private CountersignNodeMetadata resolveCountersignMetadata(BpmnModel model, String nodeId) {
        BaseElement element = model.getFlowElement(nodeId);
        if (element == null) {
            return null;
        }
        String approvalMode = extensionValue(element, "approvalMode");
        if (!"SEQUENTIAL".equals(approvalMode) && !"PARALLEL".equals(approvalMode)) {
            return null;
        }
        List<String> userIds = commaSeparatedValue(extensionValue(element, "userIds"));
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
                userIds
        );
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
                resultSet.getString("member_status")
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
            List<String> userIds
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
            String memberStatus
    ) {
    }
}
