package com.westflow.ai.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

/**
 * Skill 内容加载器，支持 classpath 与本地文件路径。
 */
@Component
public class AiSkillContentLoader {

    private final DefaultResourceLoader resourceLoader = new DefaultResourceLoader();

    /**
     * 读取 Skill 正文，读取失败时返回空字符串。
     */
    public String load(String skillPath) {
        if (skillPath == null || skillPath.isBlank()) {
            return "";
        }
        String normalizedPath = skillPath.trim();
        if (normalizedPath.startsWith("classpath:")) {
            return loadClasspathResource(normalizedPath);
        }
        return loadFileResource(normalizedPath);
    }

    private String loadClasspathResource(String skillPath) {
        try {
            Resource resource = resourceLoader.getResource(skillPath);
            if (!resource.exists()) {
                return "";
            }
            try (var inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException exception) {
            return "";
        }
    }

    private String loadFileResource(String skillPath) {
        try {
            Path path = Path.of(skillPath);
            if (!Files.exists(path)) {
                return "";
            }
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException exception) {
            return "";
        }
    }
}
