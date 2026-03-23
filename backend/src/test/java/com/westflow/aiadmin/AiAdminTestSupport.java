package com.westflow.aiadmin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * AI 管理后台测试公共辅助。
 */
public final class AiAdminTestSupport {

    private AiAdminTestSupport() {
    }

    /**
     * 使用平台管理员登录并返回访问令牌。
     */
    public static String loginAdmin(MockMvc mockMvc, ObjectMapper objectMapper) throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType("application/json")
                        .content("""
                                {
                                  "username": "admin",
                                  "password": "admin123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        JsonNode data = objectMapper.readTree(response).path("data");
        return data.path("accessToken").asText();
    }

    /**
     * 为带 Token 的请求统一加认证头。
     */
    public static MockHttpServletRequestBuilder withBearer(MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token);
    }
}
