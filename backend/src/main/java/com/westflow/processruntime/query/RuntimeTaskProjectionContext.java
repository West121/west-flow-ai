package com.westflow.processruntime.query;

import com.westflow.processdef.model.PublishedProcessDefinition;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.flowable.identitylink.api.IdentityLink;

/**
 * 运行态任务投影过程中的请求级缓存。
 */
public final class RuntimeTaskProjectionContext {

    private final Map<String, Map<String, Object>> runtimeVariablesByInstanceId;
    private final Map<String, PublishedProcessDefinition> definitionByInstanceId;
    private final Map<String, List<IdentityLink>> identityLinksByTaskId;

    private RuntimeTaskProjectionContext(
            Map<String, Map<String, Object>> runtimeVariablesByInstanceId,
            Map<String, PublishedProcessDefinition> definitionByInstanceId,
            Map<String, List<IdentityLink>> identityLinksByTaskId
    ) {
        this.runtimeVariablesByInstanceId = runtimeVariablesByInstanceId;
        this.definitionByInstanceId = definitionByInstanceId;
        this.identityLinksByTaskId = identityLinksByTaskId;
    }

    public static RuntimeTaskProjectionContext create() {
        return new RuntimeTaskProjectionContext(new HashMap<>(), new HashMap<>(), new HashMap<>());
    }

    public Map<String, Map<String, Object>> runtimeVariablesByInstanceId() {
        return runtimeVariablesByInstanceId;
    }

    public Map<String, PublishedProcessDefinition> definitionByInstanceId() {
        return definitionByInstanceId;
    }

    public Map<String, List<IdentityLink>> identityLinksByTaskId() {
        return identityLinksByTaskId;
    }
}
