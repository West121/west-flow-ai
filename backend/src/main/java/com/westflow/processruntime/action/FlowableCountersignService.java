package com.westflow.processruntime.action;

import com.westflow.flowable.FlowableEngineFacade;
import com.westflow.processruntime.api.response.CountersignTaskGroupMemberResponse;
import com.westflow.processruntime.api.response.CountersignTaskGroupResponse;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.flowable.task.api.Task;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Service;

/**
 * 维护会签节点的任务组与成员快照，便于后续详情页和票签能力复用。
 */
@Service
@RequiredArgsConstructor
@ConditionalOnBean(FlowableEngineFacade.class)
public class FlowableCountersignService {

    private final RuntimeCountersignSnapshotService runtimeCountersignSnapshotService;
    private final RuntimeCountersignVoteService runtimeCountersignVoteService;

    /**
     * 流程实例启动后，为当前激活的会签节点建立任务组快照。
     */
    public void initializeTaskGroups(String processDefinitionId, String processInstanceId) {
        runtimeCountersignSnapshotService.initializeTaskGroups(processDefinitionId, processInstanceId);
    }

    /**
     * 任务完成后刷新会签分组状态，把下一个活动任务绑定到成员快照上。
     */
    public void syncAfterTaskCompleted(String processDefinitionId, String processInstanceId, String completedTaskId) {
        runtimeCountersignSnapshotService.syncAfterTaskCompleted(processDefinitionId, processInstanceId, completedTaskId);
    }

    /**
     * 在任务完成前计算会签决议变量，确保 Flowable 在本次完成动作里就能命中完成条件。
     */
    public Map<String, Object> prepareCompletionVariables(
            String processDefinitionId,
            Task task,
            String action
    ) {
        return runtimeCountersignVoteService.prepareCompletionVariables(processDefinitionId, task, action);
    }

    /**
     * 查询流程实例下的会签任务组快照。
     */
    public List<CountersignTaskGroupResponse> queryTaskGroups(String processInstanceId) {
        return runtimeCountersignSnapshotService.queryTaskGroups(processInstanceId);
    }

    /**
     * 会签发生驳回/退回重建时，移除当前节点的运行中快照并按最新活动任务重新同步。
     */
    public void rebuildTaskGroupsForNode(String processDefinitionId, String processInstanceId, String nodeId) {
        runtimeCountersignSnapshotService.rebuildTaskGroupsForNode(processDefinitionId, processInstanceId, nodeId);
    }
}
