package com.westflow.orchestrator.service;

import com.westflow.orchestrator.api.OrchestratorManualScanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class OrchestratorServiceTest {

    @Autowired
    private OrchestratorService orchestratorService;

    @Test
    void shouldProduceFourDemoAutomationExecutions() {
        OrchestratorManualScanResponse response = orchestratorService.manualScan();

        assertThat(response.runId()).startsWith("orc_scan_");
        assertThat(response.results()).hasSize(4);
        assertThat(response.results()).extracting(item -> item.automationType().name())
                .containsExactlyInAnyOrder(
                        "TIMEOUT_APPROVAL",
                        "AUTO_REMINDER",
                        "TIMER_NODE",
                        "TRIGGER_NODE"
                );
    }
}
