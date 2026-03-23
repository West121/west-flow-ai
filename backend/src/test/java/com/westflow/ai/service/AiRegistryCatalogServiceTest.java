package com.westflow.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.westflow.ai.mapper.AiRegistryMapper;
import com.westflow.ai.skill.AiSkillContentLoader;
import com.westflow.identity.mapper.AuthUserMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 数据库注册表目录服务测试。
 */
class AiRegistryCatalogServiceTest {

    @Test
    void shouldFilterSkillsByCapabilityAndLoadSkillContent() throws Exception {
        AiRegistryMapper aiRegistryMapper = mock(AiRegistryMapper.class);
        AuthUserMapper authUserMapper = mock(AuthUserMapper.class);
        Path skillFile = Files.createTempFile("workflow-design", ".md");
        Files.writeString(skillFile, "# 流程设计技能\n请输出流程设计建议。");

        when(authUserMapper.selectAiCapabilitiesByUserId("usr_001"))
                .thenReturn(List.of("ai:workflow:design", "ai:copilot:open"));
        when(aiRegistryMapper.selectEnabledSkillRegistries()).thenReturn(List.of(
                new AiRegistryMapper.AiSkillRegistryRow(
                        "ai_skill_001",
                        "workflow-design-skill",
                        "流程设计技能",
                        skillFile.toString(),
                        "ai:workflow:design",
                        true,
                        "{\"businessDomains\":[\"OA\"],\"triggerKeywords\":[\"流程\",\"设计\"],\"priority\":90}"
                ),
                new AiRegistryMapper.AiSkillRegistryRow(
                        "ai_skill_002",
                        "plm-private-skill",
                        "PLM 私有技能",
                        skillFile.toString(),
                        "ai:plm:assist",
                        true,
                        "{\"businessDomains\":[\"PLM\"],\"triggerKeywords\":[\"PLM\"],\"priority\":80}"
                )
        ));

        AiRegistryCatalogService service = new AiRegistryCatalogService(
                aiRegistryMapper,
                authUserMapper,
                new ObjectMapper(),
                new AiSkillContentLoader()
        );

        List<AiRegistryCatalogService.AiSkillCatalogItem> matched = service.matchSkills(
                "usr_001",
                "请帮我设计一条请假流程",
                "OA",
                List.of()
        );

        assertThat(matched).hasSize(1);
        assertThat(matched.get(0).skillCode()).isEqualTo("workflow-design-skill");
        assertThat(matched.get(0).content()).contains("流程设计技能");
    }
}
