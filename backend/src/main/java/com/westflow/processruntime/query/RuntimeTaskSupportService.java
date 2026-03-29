package com.westflow.processruntime.query;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.identity.mapper.IdentityAccessMapper;
import com.westflow.identity.response.CurrentUserResponse;
import com.westflow.identity.service.IdentityAuthService;
import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.bpmn.model.BaseElement;
import org.flowable.bpmn.model.BpmnModel;
import org.flowable.engine.history.HistoricProcessInstance;
import org.flowable.identitylink.api.IdentityLink;
import org.flowable.task.api.Task;
import org.flowable.task.api.history.HistoricTaskInstance;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RuntimeTaskSupportService {

    private static final ZoneId TIME_ZONE = ZoneId.of("Asia/Shanghai");

    private final FlowableEngineFacade flowableEngineFacade;
    private final IdentityAccessMapper identityAccessMapper;
    private final IdentityAuthService identityAuthService;
    private final RuntimeTaskVisibilityService runtimeTaskVisibilityService;

    public List<String> candidateUsers(String taskId) {
        return candidateUsers(identityLinksForTask(taskId));
    }

    public List<String> candidateUsers(List<IdentityLink> identityLinks) {
        return identityLinks.stream()
                .filter(link -> "candidate".equals(link.getType()))
                .map(IdentityLink::getUserId)
                .filter(userId -> userId != null && !userId.isBlank())
                .distinct()
                .toList();
    }

    public List<String> candidateGroups(String taskId) {
        return candidateGroups(identityLinksForTask(taskId));
    }

    public List<String> candidateGroups(List<IdentityLink> identityLinks) {
        return identityLinks.stream()
                .filter(link -> "candidate".equals(link.getType()))
                .map(IdentityLink::getGroupId)
                .filter(groupId -> groupId != null && !groupId.isBlank())
                .distinct()
                .toList();
    }

    public List<IdentityLink> identityLinksForTask(String taskId) {
        return flowableEngineFacade.taskService().getIdentityLinksForTask(taskId);
    }

    public boolean isCurrentUserCandidate(Task task, List<String> candidateUserIds, List<String> candidateGroupIds) {
        if (task.getAssignee() != null) {
            return false;
        }
        String currentUserId = identityAuthService.currentUser().userId();
        if (candidateUserIds.contains(currentUserId)) {
            return true;
        }
        return !Collections.disjoint(candidateGroupIds, currentCandidateGroupIds());
    }

    public String resolveAssignmentMode(List<String> candidateUserIds, List<String> candidateGroupIds, String assigneeUserId) {
        if (!candidateGroupIds.isEmpty()) {
            return "DEPARTMENT";
        }
        if (assigneeUserId != null || !candidateUserIds.isEmpty()) {
            return "USER";
        }
        return null;
    }

    public OffsetDateTime readTimeValue(Map<String, Object> localVariables) {
        if (localVariables == null || localVariables.isEmpty()) {
            return null;
        }
        Object value = localVariables.get("westflowReadTime");
        if (value instanceof Timestamp timestamp) {
            return OffsetDateTime.ofInstant(timestamp.toInstant(), TIME_ZONE);
        }
        if (value instanceof java.util.Date date) {
            return OffsetDateTime.ofInstant(date.toInstant(), TIME_ZONE);
        }
        return null;
    }

    public String resolveTaskSemanticMode(Task task) {
        if (task == null) {
            return null;
        }
        String localTaskSemanticMode = stringValue(runtimeTaskVisibilityService.taskLocalVariables(task.getId(), RuntimeTaskQueryContext.create()).get("westflowTaskSemanticMode"));
        if (localTaskSemanticMode != null) {
            return localTaskSemanticMode;
        }
        return resolveTaskSemanticMode(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
    }

    public String resolveHistoricTaskSemanticMode(HistoricTaskInstance task) {
        if (task == null) {
            return null;
        }
        String localTaskSemanticMode = stringValue(historicTaskLocalVariables(task.getId()).get("westflowTaskSemanticMode"));
        if (localTaskSemanticMode != null) {
            return localTaskSemanticMode;
        }
        return resolveTaskSemanticMode(task.getProcessDefinitionId(), task.getTaskDefinitionKey());
    }

    public String resolveTaskSemanticMode(String engineProcessDefinitionId, String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return null;
        }
        BpmnModel model = flowableEngineFacade.repositoryService().getBpmnModel(engineProcessDefinitionId);
        if (model == null) {
            return null;
        }
        BaseElement element = model.getFlowElement(nodeId);
        if (element == null) {
            return null;
        }
        List<org.flowable.bpmn.model.ExtensionAttribute> attrs = element.getAttributes().get("ccSemanticMode");
        if (attrs == null || attrs.isEmpty() || attrs.get(0).getValue() == null || attrs.get(0).getValue().isBlank()) {
            return null;
        }
        return attrs.get(0).getValue();
    }

    public boolean supportsSemanticRead(String taskSemanticMode) {
        return taskSemanticMode == null
                || List.of("cc", "supervise", "meeting", "read", "circulate").contains(taskSemanticMode);
    }

    public String resolveReadEventType(String taskSemanticMode) {
        return switch (taskSemanticMode == null ? "cc" : taskSemanticMode) {
            case "supervise" -> "TASK_SUPERVISE_READ";
            case "meeting" -> "TASK_MEETING_READ";
            case "read" -> "TASK_READ_CONFIRM";
            case "circulate" -> "TASK_CIRCULATE_READ";
            default -> "TASK_READ";
        };
    }

    public String resolveReadEventName(String taskSemanticMode) {
        return switch (taskSemanticMode == null ? "cc" : taskSemanticMode) {
            case "supervise" -> "督办已阅";
            case "meeting" -> "会办已阅";
            case "read" -> "阅办已阅";
            case "circulate" -> "传阅已阅";
            default -> "抄送已阅";
        };
    }

    public String resolveReadActionCategory(String taskSemanticMode) {
        return switch (taskSemanticMode == null ? "cc" : taskSemanticMode) {
            case "supervise" -> "SUPERVISE";
            case "meeting" -> "MEETING";
            case "read" -> "READ";
            case "circulate" -> "CIRCULATE";
            default -> "CC";
        };
    }

    public String resolveTaskStatus(Task task) {
        return resolveTaskStatus(task, identityLinksForTask(task.getId()), runtimeTaskVisibilityService.resolveTaskKind(task, RuntimeTaskQueryContext.create()), RuntimeTaskQueryContext.create().taskLocalVariablesByTaskId());
    }

    public String resolveTaskStatus(
            Task task,
            List<IdentityLink> identityLinks,
            String taskKind,
            Map<String, Map<String, Object>> taskLocalVariablesByTaskId
    ) {
        if ("CC".equals(taskKind)) {
            return readTimeValue(runtimeTaskVisibilityService.taskLocalVariables(
                    task.getId(),
                    RuntimeTaskQueryContext.of(taskLocalVariablesByTaskId, new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>(), new java.util.HashMap<>()))
            ) == null ? "CC_PENDING" : "CC_READ";
        }
        if ("DELEGATED".equals(resolveActingMode(task, null))) {
            return "DELEGATED";
        }
        if ("ADD_SIGN".equals(taskKind)) {
            return "PENDING";
        }
        if (task.getOwner() != null && task.getAssignee() != null && !task.getOwner().equals(task.getAssignee())) {
            return "DELEGATED";
        }
        List<String> candidateUserIds = candidateUsers(identityLinks);
        List<String> candidateGroupIds = candidateGroups(identityLinks);
        return task.getAssignee() == null
                && (!candidateUserIds.isEmpty() || !candidateGroupIds.isEmpty())
                ? "PENDING_CLAIM"
                : "PENDING";
    }

    public String resolveHistoricTaskStatus(HistoricTaskInstance task, HistoricProcessInstance processInstance) {
        String taskKind = task == null ? null : resolveHistoricTaskKind(task);
        if (task != null) {
            Map<String, Object> localVariables = historicTaskLocalVariables(task.getId());
            String action = stringValue(localVariables.get("westflowAction"));
            if ("TAKE_BACK".equals(action)) {
                return "TAKEN_BACK";
            }
            if ("HANDOVER".equals(action)) {
                return "HANDOVERED";
            }
            if ("CC".equals(taskKind)) {
                return readTimeValue(localVariables) == null ? "CC_PENDING" : "CC_READ";
            }
            if (task.getDeleteReason() != null && !task.getDeleteReason().isBlank()) {
                return "REVOKED";
            }
            return "COMPLETED";
        }
        if (processInstance != null && "WESTFLOW_REVOKED".equals(processInstance.getDeleteReason())) {
            return "REVOKED";
        }
        return "COMPLETED";
    }

    public String resolveActingMode(Task activeTask, HistoricTaskInstance historicTask) {
        Map<String, Object> localVariables = activeTask != null
                ? runtimeTaskVisibilityService.taskLocalVariables(activeTask.getId(), RuntimeTaskQueryContext.create())
                : historicTask == null ? Map.of() : historicTaskLocalVariables(historicTask.getId());
        String explicitMode = stringValue(localVariables.get("westflowActingMode"));
        if (explicitMode != null) {
            return explicitMode;
        }
        String ownerUserId = activeTask != null ? activeTask.getOwner() : historicTask == null ? null : historicTask.getOwner();
        String assigneeUserId = activeTask != null ? activeTask.getAssignee() : historicTask == null ? null : historicTask.getAssignee();
        if (ownerUserId != null && assigneeUserId != null && !ownerUserId.equals(assigneeUserId)) {
            return "DELEGATE";
        }
        return null;
    }

    private List<String> currentCandidateGroupIds() {
        CurrentUserResponse currentUser = identityAuthService.currentUser();
        LinkedHashSet<String> groupIds = new LinkedHashSet<>();
        currentUser.postAssignments().stream()
                .filter(assignment -> assignment.postId().equals(currentUser.activePostId()))
                .findFirst()
                .ifPresentOrElse(
                        assignment -> assignment.roleIds().stream()
                                .filter(roleId -> roleId != null && !roleId.isBlank())
                                .forEach(groupIds::add),
                        () -> identityAccessMapper.selectRoleIdsByUserId(currentUser.userId()).stream()
                                .filter(roleId -> roleId != null && !roleId.isBlank())
                                .forEach(groupIds::add)
                );
        if (currentUser.activeDepartmentId() != null && !currentUser.activeDepartmentId().isBlank()) {
            groupIds.add(currentUser.activeDepartmentId());
        }
        return List.copyOf(groupIds);
    }

    private Map<String, Object> historicTaskLocalVariables(String taskId) {
        java.util.LinkedHashMap<String, Object> variables = new java.util.LinkedHashMap<>();
        flowableEngineFacade.historyService()
                .createHistoricVariableInstanceQuery()
                .taskId(taskId)
                .list()
                .forEach(variable -> variables.put(variable.getVariableName(), variable.getValue()));
        return variables;
    }

    private String resolveHistoricTaskKind(HistoricTaskInstance task) {
        String historicTaskKind = stringValue(historicTaskLocalVariables(task.getId()).get("westflowTaskKind"));
        if (historicTaskKind != null) {
            return historicTaskKind;
        }
        return runtimeTaskVisibilityService.resolveTaskKind(task.getProcessDefinitionId(), task.getTaskDefinitionKey(), RuntimeTaskQueryContext.create());
    }

    private String stringValue(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value);
        return text.isBlank() ? null : text;
    }
}
