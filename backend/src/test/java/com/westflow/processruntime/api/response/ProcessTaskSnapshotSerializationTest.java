package com.westflow.processruntime.api.response;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ProcessTaskSnapshotSerializationTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldKeepStableJsonFieldsWhenSerializingRuntimeTaskSnapshots() throws Exception {
        ProcessTaskSnapshot snapshot = new ProcessTaskSnapshot(
                "task_001",
                "approve_manager",
                "部门负责人审批",
                "NORMAL",
                "PENDING",
                "USER",
                List.of("usr_002"),
                List.of(),
                "usr_002",
                null,
                null,
                null,
                null
        );
        StartProcessResponse startResponse = new StartProcessResponse(
                "process_def_001",
                "instance_001",
                "RUNNING",
                List.of(snapshot)
        );
        CompleteTaskResponse completeResponse = new CompleteTaskResponse(
                "instance_001",
                "task_001",
                "RUNNING",
                List.of(snapshot)
        );

        JsonNode startJson = objectMapper.readTree(objectMapper.writeValueAsBytes(startResponse));
        JsonNode completeJson = objectMapper.readTree(objectMapper.writeValueAsBytes(completeResponse));

        assertThat(startJson.path("activeTasks").get(0).path("taskId").asText()).isEqualTo("task_001");
        assertThat(startJson.path("activeTasks").get(0).path("nodeId").asText()).isEqualTo("approve_manager");
        assertThat(startJson.path("activeTasks").get(0).path("assigneeUserId").asText()).isEqualTo("usr_002");
        assertThat(startJson.path("activeTasks").get(0).path("candidateUserIds").isArray()).isTrue();
        assertThat(startJson.path("activeTasks").get(0).path("candidateGroupIds").isArray()).isTrue();

        assertThat(completeJson.path("nextTasks").get(0).path("taskId").asText()).isEqualTo("task_001");
        assertThat(completeJson.path("nextTasks").get(0).path("nodeName").asText()).isEqualTo("部门负责人审批");
        assertThat(completeJson.path("nextTasks").get(0).path("taskKind").asText()).isEqualTo("NORMAL");
        assertThat(completeJson.path("nextTasks").get(0).path("status").asText()).isEqualTo("PENDING");
    }
}
