package com.westflow.common.error;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldMapValidationErrorsToFrozenContract() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "",
                                  "password": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);

        assertThat(body.get("code").asText()).isEqualTo("VALIDATION.FIELD_INVALID");
        assertThat(body.get("message").asText()).isEqualTo("请求参数校验失败");
        assertThat(body.get("requestId").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/auth/login");
        assertThat(body.get("timestamp").asText()).isNotBlank();
        assertThat(body.get("fieldErrors").isArray()).isTrue();
        assertThat(body.get("fieldErrors").get(0).get("field").asText()).isIn("username", "password");
        assertThat(body.get("fieldErrors").get(0).get("code").asText()).isEqualTo("REQUIRED");
    }

    @Test
    void shouldMapPermissionFailuresToFrozenContract() throws Exception {
        String loginResponse = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "lisi",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String token = objectMapper.readTree(loginResponse)
                .path("data")
                .path("accessToken")
                .asText();

        String response = mockMvc.perform(get("/api/v1/auth/permission-probe")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode body = objectMapper.readTree(response);

        assertThat(body.get("code").asText()).isEqualTo("AUTH.FORBIDDEN");
        assertThat(body.get("message").asText()).isEqualTo("无权限访问");
        assertThat(body.get("requestId").asText()).isNotBlank();
        assertThat(body.get("path").asText()).isEqualTo("/api/v1/auth/permission-probe");
        assertThat(body.get("timestamp").asText()).isNotBlank();
    }
}
