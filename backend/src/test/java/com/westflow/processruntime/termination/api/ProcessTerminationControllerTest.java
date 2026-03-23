package com.westflow.processruntime.termination.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.processruntime.termination.model.ProcessTerminationAuditEventType;
import com.westflow.processruntime.termination.model.ProcessTerminationPropagationPolicy;
import com.westflow.processruntime.termination.model.ProcessTerminationScope;
import com.westflow.processruntime.termination.service.ProcessTerminationStrategyService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ProcessTerminationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProcessTerminationStrategyService processTerminationStrategyService;

    @Test
    void shouldPreviewSnapshotAndAuditTrailTerminationStrategy() throws Exception {
        ProcessTerminationPlanResponse plan = new ProcessTerminationPlanResponse(
                "root_1",
                "child_1",
                ProcessTerminationScope.CHILD,
                ProcessTerminationPropagationPolicy.CASCADE_ALL,
                "终止原因",
                "usr_admin",
                Instant.parse("2026-03-23T08:00:00Z"),
                2,
                List.of()
        );
        ProcessTerminationSnapshotResponse snapshot = new ProcessTerminationSnapshotResponse(
                "root_1",
                ProcessTerminationScope.CHILD,
                ProcessTerminationPropagationPolicy.CASCADE_ALL,
                "终止原因",
                "usr_admin",
                "scope=CHILD, propagation=CASCADE_ALL, targets=2",
                2,
                Instant.parse("2026-03-23T08:01:00Z"),
                List.of()
        );
        ProcessTerminationAuditResponse audit = new ProcessTerminationAuditResponse(
                "audit_001",
                "root_1",
                "child_1",
                "root_1",
                "SUBPROCESS",
                ProcessTerminationScope.CHILD,
                ProcessTerminationPropagationPolicy.CASCADE_ALL,
                ProcessTerminationAuditEventType.PLANNED,
                "PLANNED",
                "终止原因",
                "usr_admin",
                "{}",
                Instant.parse("2026-03-23T08:02:00Z"),
                Instant.parse("2026-03-23T08:02:00Z")
        );

        when(processTerminationStrategyService.preview(any())).thenReturn(plan);
        when(processTerminationStrategyService.snapshot(any())).thenReturn(snapshot);
        when(processTerminationStrategyService.listAuditTrail("root_1")).thenReturn(List.of(audit));

        JsonNode previewBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/termination/preview")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootInstanceId": "root_1",
                                  "targetInstanceId": "child_1",
                                  "scope": "CHILD",
                                  "propagationPolicy": "CASCADE_ALL",
                                  "reason": "终止原因",
                                  "operatorUserId": "usr_admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(previewBody.path("rootInstanceId").asText()).isEqualTo("root_1");
        assertThat(previewBody.path("targetCount").asInt()).isEqualTo(2);

        JsonNode snapshotBody = objectMapper.readTree(mockMvc.perform(post("/api/v1/process-runtime/termination/snapshot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rootInstanceId": "root_1",
                                  "targetInstanceId": "child_1",
                                  "scope": "CHILD",
                                  "propagationPolicy": "CASCADE_ALL",
                                  "reason": "终止原因",
                                  "operatorUserId": "usr_admin"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(snapshotBody.path("summary").asText()).contains("CASCADE_ALL");

        JsonNode auditBody = objectMapper.readTree(mockMvc.perform(get("/api/v1/process-runtime/termination/audit-trail")
                        .param("rootInstanceId", "root_1"))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString()).path("data");
        assertThat(auditBody.isArray()).isTrue();
        assertThat(auditBody.get(0).path("auditId").asText()).isEqualTo("audit_001");
        assertThat(auditBody.get(0).path("eventType").asText()).isEqualTo("PLANNED");
    }
}
