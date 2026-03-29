package com.westflow.processruntime.query;

import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import com.westflow.processruntime.api.response.ProcessInstanceEventResponse;
import com.westflow.processruntime.api.response.ProcessTaskTraceItemResponse;
import com.westflow.processruntime.api.response.RuntimeAppendLinkResponse;
import com.westflow.processruntime.api.response.WorkflowFieldBinding;
import com.westflow.processruntime.support.RuntimeParticipantDirectoryService;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskDetailProjectionService {

    private final RuntimeParticipantDirectoryService runtimeParticipantDirectoryService;

    public Map<String, String> buildUserDisplayNameMap(
            String applicantUserId,
            Task referenceActiveTask,
            HistoricTaskInstance referenceHistoricTask,
            List<ProcessTaskTraceItemResponse> taskTrace,
            List<ProcessInstanceEventResponse> instanceEvents,
            List<RuntimeAppendLinkResponse> runtimeAppendLinks,
            List<CountersignTaskGroupResponse> countersignGroups,
            Map<String, Object> businessData,
            Map<String, Object> processFormData,
            Map<String, Object> taskFormData
    ) {
        Set<String> userIds = new LinkedHashSet<>();
        addIfPresent(userIds, applicantUserId);
        addIfPresent(userIds, referenceActiveTask == null ? null : referenceActiveTask.getAssignee());
        addIfPresent(userIds, referenceHistoricTask == null ? null : referenceHistoricTask.getAssignee());
        for (ProcessTaskTraceItemResponse item : taskTrace) {
            addIfPresent(userIds, item.assigneeUserId());
            addIfPresent(userIds, item.operatorUserId());
            addIfPresent(userIds, item.targetUserId());
            addIfPresent(userIds, item.actingForUserId());
            addIfPresent(userIds, item.delegatedByUserId());
            addIfPresent(userIds, item.handoverFromUserId());
            item.candidateUserIds().forEach(candidateUserId -> addIfPresent(userIds, candidateUserId));
        }
        for (ProcessInstanceEventResponse event : instanceEvents) {
            addIfPresent(userIds, event.operatorUserId());
            addIfPresent(userIds, event.targetUserId());
            addIfPresent(userIds, event.actingForUserId());
            addIfPresent(userIds, event.delegatedByUserId());
            addIfPresent(userIds, event.handoverFromUserId());
        }
        for (RuntimeAppendLinkResponse link : runtimeAppendLinks) {
            addIfPresent(userIds, link.targetUserId());
            addIfPresent(userIds, link.operatorUserId());
        }
        for (CountersignTaskGroupResponse group : countersignGroups) {
            group.members().forEach(member -> addIfPresent(userIds, member.assigneeUserId()));
        }
        addUserReferencesFromMap(userIds, businessData);
        addUserReferencesFromMap(userIds, processFormData);
        addUserReferencesFromMap(userIds, taskFormData);
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (String userId : userIds) {
            displayNames.put(userId, runtimeParticipantDirectoryService.resolveUserDisplayName(userId));
        }
        return Collections.unmodifiableMap(displayNames);
    }

    public Map<String, String> buildGroupDisplayNameMap(
            List<String> currentCandidateGroupIds,
            List<ProcessTaskTraceItemResponse> taskTrace
    ) {
        Set<String> groupIds = new LinkedHashSet<>();
        currentCandidateGroupIds.forEach(groupId -> addIfPresent(groupIds, groupId));
        for (ProcessTaskTraceItemResponse item : taskTrace) {
            item.candidateGroupIds().forEach(groupId -> addIfPresent(groupIds, groupId));
        }
        Map<String, String> displayNames = new LinkedHashMap<>();
        for (String groupId : groupIds) {
            displayNames.put(groupId, runtimeParticipantDirectoryService.resolveGroupDisplayName(groupId));
        }
        return Collections.unmodifiableMap(displayNames);
    }

    public List<WorkflowFieldBinding> workflowFieldBindings(Object value) {
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        List<WorkflowFieldBinding> bindings = new ArrayList<>();
        for (Object item : values) {
            Map<String, Object> binding = mapValue(item);
            if (binding.isEmpty()) {
                continue;
            }
            bindings.add(new WorkflowFieldBinding(
                    stringValue(binding.get("source")),
                    stringValue(binding.get("sourceFieldKey")),
                    stringValue(binding.get("targetFieldKey"))
            ));
        }
        return List.copyOf(bindings);
    }

    private void addUserReferencesFromMap(Set<String> userIds, Map<String, Object> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        values.forEach((key, value) -> {
            if (key == null || value == null) {
                return;
            }
            if (key.endsWith("UserId")) {
                addIfPresent(userIds, stringValue(value));
                return;
            }
            if (key.endsWith("UserIds")) {
                if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) {
                        addIfPresent(userIds, stringValue(item));
                    }
                    return;
                }
                if (value.getClass().isArray() && value instanceof Object[] array) {
                    for (Object item : array) {
                        addIfPresent(userIds, stringValue(item));
                    }
                    return;
                }
                String rawValue = stringValue(value);
                if (rawValue != null) {
                    for (String item : rawValue.split(",")) {
                        addIfPresent(userIds, item);
                    }
                }
            }
        });
    }

    private void addIfPresent(Collection<String> values, String value) {
        if (value != null && !value.isBlank()) {
            values.add(value.trim());
        }
    }

    private Map<String, Object> mapValue(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, item) -> result.put(String.valueOf(key), item));
        return result;
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
