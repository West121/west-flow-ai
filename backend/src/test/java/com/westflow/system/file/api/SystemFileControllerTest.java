package com.westflow.system.file.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.system.file.mapper.SystemFileMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SystemFileControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SystemFileMapper systemFileMapper;

    @BeforeEach
    void resetStorage() {
        systemFileMapper.clear();
    }

    @Test
    void shouldUploadReadUpdateDownloadAndDeleteFileMetadata() throws Exception {
        String token = login();

        MockMultipartFile filePart = new MockMultipartFile(
                "file",
                "contract.pdf",
                "application/pdf",
                "west-flow-file-content".getBytes()
        );

        String uploadResponse = mockMvc.perform(multipart("/api/v1/system/files")
                        .file(filePart)
                        .param("displayName", "合同附件")
                        .param("remark", "用于审批流附件")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        String fileId = objectMapper.readTree(uploadResponse).path("data").path("fileId").asText();
        assertThat(fileId).startsWith("fil_");

        String detailResponse = mockMvc.perform(get("/api/v1/system/files/" + fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode detailData = objectMapper.readTree(detailResponse).path("data");
        assertThat(detailData.path("displayName").asText()).isEqualTo("合同附件");
        assertThat(detailData.path("originalFilename").asText()).isEqualTo("contract.pdf");
        assertThat(detailData.path("status").asText()).isEqualTo("ACTIVE");

        mockMvc.perform(put("/api/v1/system/files/" + fileId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "合同附件（已更新）",
                                  "remark": "更新后的说明"
                                }
                                """))
                .andExpect(status().isOk());

        String updatedDetailResponse = mockMvc.perform(get("/api/v1/system/files/" + fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(objectMapper.readTree(updatedDetailResponse).path("data").path("displayName").asText())
                .isEqualTo("合同附件（已更新）");

        byte[] downloadBody = mockMvc.perform(get("/api/v1/system/files/" + fileId + "/download")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsByteArray();
        assertThat(new String(downloadBody)).isEqualTo("west-flow-file-content");

        mockMvc.perform(delete("/api/v1/system/files/" + fileId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String deletedPageResponse = mockMvc.perform(post("/api/v1/system/files/page")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "page": 1,
                                  "pageSize": 20,
                                  "keyword": "合同",
                                  "filters": [
                                    {
                                      "field": "status",
                                      "operator": "eq",
                                      "value": "DELETED"
                                    }
                                  ],
                                  "sorts": [
                                    {
                                      "field": "createdAt",
                                      "direction": "desc"
                                    }
                                  ],
                                  "groups": []
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        JsonNode deletedPageData = objectMapper.readTree(deletedPageResponse).path("data");
        assertThat(deletedPageData.path("total").asInt()).isEqualTo(1);
        assertThat(deletedPageData.path("records").get(0).path("status").asText()).isEqualTo("DELETED");
    }

    private String login() throws Exception {
        String response = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "username": "wangwu",
                                  "password": "password123"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();

        return objectMapper.readTree(response).path("data").path("accessToken").asText();
    }
}
