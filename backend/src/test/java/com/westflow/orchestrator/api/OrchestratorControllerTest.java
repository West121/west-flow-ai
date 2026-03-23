package com.westflow.orchestrator.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.orchestrator.model.OrchestratorAutomationType;
import com.westflow.orchestrator.model.OrchestratorExecutionStatus;
import com.westflow.orchestrator.service.OrchestratorService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OrchestratorControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrchestratorService orchestratorService;

    @Test
    void shouldReturnManualScanResponse() throws Exception {
        when(orchestratorService.manualScan()).thenReturn(new OrchestratorManualScanResponse(
                "orc_scan_001",
                Instant.parse("2026-03-23T04:00:00Z"),
                List.of(
                        new OrchestratorScanResultResponse(
                                "orc_exec_001",
                                OrchestratorAutomationType.TIMEOUT_APPROVAL,
                                "orc_target_pi_001__task_001__timeout_approval",
                                "领导审批",
                                OrchestratorExecutionStatus.SUCCEEDED,
                                "已按超时策略自动处理审批任务（action=APPROVE）"
                        ),
                        new OrchestratorScanResultResponse(
                                "orc_exec_002",
                                OrchestratorAutomationType.TIMER_NODE,
                                "orc_target_pi_001__timer_wait_node__timer_node",
                                "定时节点",
                                OrchestratorExecutionStatus.SUCCEEDED,
                                "定时节点已到点推进"
                        )
                )
        ));

        String token = login("wangwu");

        String response = mockMvc.perform(post("/api/v1/orchestrator/scans/manual")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("runId").asText()).isEqualTo("orc_scan_001");
        assertThat(data.path("results").isArray()).isTrue();
        assertThat(data.path("results").size()).isEqualTo(2);
        assertThat(data.path("results").get(0).path("automationType").asText()).isEqualTo("TIMEOUT_APPROVAL");
        assertThat(data.path("results").get(1).path("automationType").asText()).isEqualTo("TIMER_NODE");
    }

    private String login(String username) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "%s",
                                  "password": "password123"
                                }
                                """.formatted(username)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
