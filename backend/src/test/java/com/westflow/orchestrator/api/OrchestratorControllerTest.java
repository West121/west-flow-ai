package com.westflow.orchestrator.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void shouldRunManualScanForAllAutomationTypes() throws Exception {
        String token = login("wangwu");

        String response = mockMvc.perform(post("/api/v1/orchestrator/scans/manual")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode data = objectMapper.readTree(response).path("data");
        assertThat(data.path("runId").asText()).startsWith("orc_scan_");
        assertThat(data.path("scannedAt").asText()).isNotBlank();
        assertThat(data.path("results").isArray()).isTrue();
        assertThat(data.path("results").size()).isEqualTo(4);

        var types = new java.util.HashSet<String>();
        for (JsonNode item : data.path("results")) {
            types.add(item.path("automationType").asText());
            assertThat(item.path("status").asText()).isEqualTo("SUCCEEDED");
            assertThat(item.path("executionId").asText()).startsWith("orc_exec_");
        }

        assertThat(types).containsExactlyInAnyOrder(
                "TIMEOUT_APPROVAL",
                "AUTO_REMINDER",
                "TIMER_NODE",
                "TRIGGER_NODE"
        );
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
