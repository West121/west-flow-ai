package com.westflow.processruntime.termination.service;

import com.westflow.processruntime.model.ProcessLinkRecord;
import com.westflow.processruntime.model.RuntimeAppendLinkRecord;
import com.westflow.processruntime.service.ProcessLinkService;
import com.westflow.processruntime.service.RuntimeAppendLinkService;
import com.westflow.processruntime.termination.api.ProcessTerminationNodeResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationPlanResponse;
import com.westflow.processruntime.termination.api.ProcessTerminationSnapshotResponse;
import com.westflow.processruntime.termination.model.ProcessTerminationCommand;
import com.westflow.processruntime.termination.model.ProcessTerminationNodeKind;
import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessTerminationTopologyServiceTest {

    @Mock
    private ProcessLinkService processLinkService;

    @Mock
    private RuntimeAppendLinkService runtimeAppendLinkService;

    @InjectMocks
    private ProcessTerminationTopologyService topologyService;

    @Test
    void shouldPreviewCascadeAllTerminationTree() {
        when(processLinkService.listByRootInstanceId("root_1")).thenReturn(List.of(
                new ProcessLinkRecord(
                        "plink_001",
                        "root_1",
                        "root_1",
                        "child_1",
                        "node_1",
                        "subflow_1",
                        "subflow_1:1",
                        "CALL_ACTIVITY",
                        "RUNNING",
                        "TERMINATE_SUBPROCESS_ONLY",
                        "RETURN_TO_PARENT",
                        "CHILD_AND_DESCENDANTS",
                        "AUTO_RETURN",
                        "LATEST_PUBLISHED",
                        "AUTO_RETURN",
                        Instant.parse("2026-03-23T01:00:00Z"),
                        null
                ),
                new ProcessLinkRecord(
                        "plink_002",
                        "root_1",
                        "child_1",
                        "child_2",
                        "node_2",
                        "subflow_2",
                        "subflow_2:1",
                        "CALL_ACTIVITY",
                        "RUNNING",
                        "TERMINATE_SUBPROCESS_ONLY",
                        "RETURN_TO_PARENT",
                        "CHILD_ONLY",
                        "AUTO_RETURN",
                        "LATEST_PUBLISHED",
                        "AUTO_RETURN",
                        Instant.parse("2026-03-23T01:05:00Z"),
                        null
                )
        ));
        when(runtimeAppendLinkService.listByRootInstanceId("root_1")).thenReturn(List.of(
                new RuntimeAppendLinkRecord(
                        "alink_001",
                        "root_1",
                        "root_1",
                        "task_1",
                        "node_append_1",
                        "TASK",
                        "ADHOC_TASK",
                        "SERIAL_AFTER_CURRENT",
                        "target_task_1",
                        null,
                        "usr_002",
                        null,
                        null,
                        "RUNNING",
                        "APPEND",
                        "usr_001",
                        "追加复核",
                        Instant.parse("2026-03-23T01:06:00Z"),
                        null
                ),
                new RuntimeAppendLinkRecord(
                        "alink_002",
                        "root_1",
                        "child_1",
                        "task_2",
                        "node_append_2",
                        "SUBPROCESS",
                        "ADHOC_SUBPROCESS",
                        "TERMINATE_PARENT_AND_GENERATED",
                        null,
                        "child_append_1",
                        null,
                        "subflow_append_1",
                        "subflow_append_1:1",
                        "RUNNING",
                        "DYNAMIC_BUILD",
                        "system",
                        "动态构建子流程",
                        Instant.parse("2026-03-23T01:10:00Z"),
                        null
                )
        ));

        ProcessTerminationCommand command = new ProcessTerminationCommand(
                "root_1",
                null,
                ProcessTerminationScope.ROOT,
                ProcessTerminationPropagationPolicy.CASCADE_ALL,
                "终止测试",
                "usr_admin"
        );

        ProcessTerminationPlanResponse plan = topologyService.preview(command);
        ProcessTerminationSnapshotResponse snapshot = topologyService.snapshot(command);

        assertThat(plan.rootInstanceId()).isEqualTo("root_1");
        assertThat(plan.targetCount()).isEqualTo(4);
        assertThat(plan.nodes()).hasSize(2);
        assertThat(flattenKinds(plan.nodes())).containsExactlyInAnyOrder(
                ProcessTerminationNodeKind.SUBPROCESS.name(),
                ProcessTerminationNodeKind.SUBPROCESS.name(),
                ProcessTerminationNodeKind.APPEND_TASK.name(),
                ProcessTerminationNodeKind.APPEND_SUBPROCESS.name()
        );

        assertThat(snapshot.summary()).contains("CASCADE_ALL");
        assertThat(snapshot.nodes()).hasSize(2);
    }

    @Test
    void shouldStopDescendingWhenCallScopeIsChildOnly() {
        when(processLinkService.listByRootInstanceId("root_1")).thenReturn(List.of(
                new ProcessLinkRecord(
                        "plink_001",
                        "root_1",
                        "root_1",
                        "child_1",
                        "node_1",
                        "subflow_1",
                        "subflow_1:1",
                        "CALL_ACTIVITY",
                        "RUNNING",
                        "TERMINATE_SUBPROCESS_ONLY",
                        "RETURN_TO_PARENT",
                        "CHILD_ONLY",
                        "AUTO_RETURN",
                        "LATEST_PUBLISHED",
                        "AUTO_RETURN",
                        Instant.parse("2026-03-23T01:00:00Z"),
                        null
                ),
                new ProcessLinkRecord(
                        "plink_002",
                        "root_1",
                        "child_1",
                        "child_2",
                        "node_2",
                        "subflow_2",
                        "subflow_2:1",
                        "CALL_ACTIVITY",
                        "RUNNING",
                        "TERMINATE_SUBPROCESS_ONLY",
                        "RETURN_TO_PARENT",
                        "CHILD_ONLY",
                        "AUTO_RETURN",
                        "LATEST_PUBLISHED",
                        "AUTO_RETURN",
                        Instant.parse("2026-03-23T01:05:00Z"),
                        null
                )
        ));
        when(runtimeAppendLinkService.listByRootInstanceId("root_1")).thenReturn(List.of());

        ProcessTerminationPlanResponse plan = topologyService.preview(new ProcessTerminationCommand(
                "root_1",
                null,
                ProcessTerminationScope.ROOT,
                ProcessTerminationPropagationPolicy.CASCADE_CHILDREN,
                "终止测试",
                "usr_admin"
        ));

        assertThat(plan.targetCount()).isEqualTo(1);
        assertThat(plan.nodes()).singleElement().satisfies(node -> {
            assertThat(node.targetId()).isEqualTo("child_1");
            assertThat(node.children()).isEmpty();
        });
    }

    @Test
    void shouldPreviewChildSubtreeOnlyWhenScopeIsChild() {
        when(processLinkService.listByRootInstanceId("root_1")).thenReturn(List.of(
                new ProcessLinkRecord(
                        "plink_001",
                        "root_1",
                        "root_1",
                        "child_1",
                        "node_1",
                        "subflow_1",
                        "subflow_1:1",
                        "CALL_ACTIVITY",
                        "RUNNING",
                        "TERMINATE_SUBPROCESS_ONLY",
                        "RETURN_TO_PARENT",
                        "CHILD_ONLY",
                        "AUTO_RETURN",
                        "LATEST_PUBLISHED",
                        "AUTO_RETURN",
                        Instant.parse("2026-03-23T01:00:00Z"),
                        null
                ),
                new ProcessLinkRecord(
                        "plink_002",
                        "root_1",
                        "child_1",
                        "child_2",
                        "node_2",
                        "subflow_2",
                        "subflow_2:1",
                        "CALL_ACTIVITY",
                        "RUNNING",
                        "TERMINATE_SUBPROCESS_ONLY",
                        "RETURN_TO_PARENT",
                        "CHILD_ONLY",
                        "AUTO_RETURN",
                        "LATEST_PUBLISHED",
                        "AUTO_RETURN",
                        Instant.parse("2026-03-23T01:05:00Z"),
                        null
                )
        ));
        when(runtimeAppendLinkService.listByRootInstanceId("root_1")).thenReturn(List.of());

        ProcessTerminationPlanResponse plan = topologyService.preview(new ProcessTerminationCommand(
                "root_1",
                "child_1",
                ProcessTerminationScope.CHILD,
                ProcessTerminationPropagationPolicy.CASCADE_CHILDREN,
                "终止子流程",
                "usr_admin"
        ));

        assertThat(plan.targetInstanceId()).isEqualTo("child_1");
        assertThat(plan.nodes()).singleElement().satisfies(node -> {
            assertThat(node.targetId()).isEqualTo("child_1");
            assertThat(node.targetKind()).isEqualTo(ProcessTerminationNodeKind.SUBPROCESS.name());
            assertThat(node.children()).isEmpty();
        });
    }

    private List<String> flattenKinds(List<ProcessTerminationNodeResponse> nodes) {
        return nodes.stream()
                .flatMap(node -> java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(node.targetKind()),
                        flattenKinds(node.children()).stream()
                ))
                .collect(Collectors.toList());
    }
}
