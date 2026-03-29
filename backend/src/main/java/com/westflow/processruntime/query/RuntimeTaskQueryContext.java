package com.westflow.processruntime.query;

import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行态任务查询的请求级上下文，承接查询链上的临时缓存。
 */
public final class RuntimeTaskQueryContext {

    private final Map<String, Map<String, Object>> taskLocalVariablesByTaskId;
    private final Map<String, String> taskKindByNodeKey;
    private final Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId;
    private final Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey;
    private final Map<String, Boolean> sourceTaskCompletedByTaskId;

    private RuntimeTaskQueryContext(
            Map<String, Map<String, Object>> taskLocalVariablesByTaskId,
            Map<String, String> taskKindByNodeKey,
            Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId,
            Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey,
            Map<String, Boolean> sourceTaskCompletedByTaskId
    ) {
        this.taskLocalVariablesByTaskId = taskLocalVariablesByTaskId;
        this.taskKindByNodeKey = taskKindByNodeKey;
        this.appendLinksByInstanceId = appendLinksByInstanceId;
        this.blockingDynamicBuilderNodeIdsByTargetKey = blockingDynamicBuilderNodeIdsByTargetKey;
        this.sourceTaskCompletedByTaskId = sourceTaskCompletedByTaskId;
    }

    public static RuntimeTaskQueryContext create() {
        return new RuntimeTaskQueryContext(
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        );
    }

    public static RuntimeTaskQueryContext of(
            Map<String, Map<String, Object>> taskLocalVariablesByTaskId,
            Map<String, String> taskKindByNodeKey,
            Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId,
            Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey,
            Map<String, Boolean> sourceTaskCompletedByTaskId
    ) {
        return new RuntimeTaskQueryContext(
                taskLocalVariablesByTaskId,
                taskKindByNodeKey,
                appendLinksByInstanceId,
                blockingDynamicBuilderNodeIdsByTargetKey,
                sourceTaskCompletedByTaskId
        );
    }

    public Map<String, Map<String, Object>> taskLocalVariablesByTaskId() {
        return taskLocalVariablesByTaskId;
    }

    public Map<String, String> taskKindByNodeKey() {
        return taskKindByNodeKey;
    }

    public Map<String, List<RuntimeAppendLinkRecord>> appendLinksByInstanceId() {
        return appendLinksByInstanceId;
    }

    public Map<String, List<String>> blockingDynamicBuilderNodeIdsByTargetKey() {
        return blockingDynamicBuilderNodeIdsByTargetKey;
    }

    public Map<String, Boolean> sourceTaskCompletedByTaskId() {
        return sourceTaskCompletedByTaskId;
    }
}
