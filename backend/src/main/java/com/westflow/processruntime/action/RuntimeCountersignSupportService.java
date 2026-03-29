package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeCountersignSupportService {

    private final FlowableEngineFacade flowableEngineFacade;

    CountersignNodeMetadata resolveCountersignMetadata(BpmnModel model, String processInstanceId, String nodeId) {
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

    List<String> runtimeCountersignAssignees(String processInstanceId, String nodeId) {
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

    String countersignDecisionVariable(String nodeId) {
        return "wfCountersignDecision_" + nodeId;
    }

    String countersignCollectionVariable(String nodeId) {
        return "wfCountersignAssignees_" + nodeId;
    }

    int resolveVoteWeight(CountersignNodeMetadata metadata, String userId) {
        if (!"VOTE".equals(metadata.approvalMode())) {
            return 0;
        }
        return Optional.ofNullable(metadata.voteWeights().get(userId)).orElse(1);
    }

    TaskGroupRecord mapTaskGroup(ResultSet resultSet, int rowNum) throws SQLException {
        return new TaskGroupRecord(
                resultSet.getString("id"),
                resultSet.getString("process_instance_id"),
                resultSet.getString("node_id"),
                resultSet.getString("node_name"),
                resultSet.getString("approval_mode"),
                resultSet.getString("group_status")
        );
    }

    TaskGroupMemberRecord mapTaskGroupMember(ResultSet resultSet, int rowNum) throws SQLException {
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

    VoteSnapshotRecord mapVoteSnapshot(ResultSet resultSet, int rowNum) throws SQLException {
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
}
