package com.westflow.system.log.service;

import com.westflow.common.api.RequestContext;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

/**
 * 基于 ip2region xdb 的离线 IP 地理解析服务。
 */
@Service
@Slf4j
public class IpRegionService {

    private static final String XDB_RESOURCE = "ip2region/ip2region_v4.xdb";

    private final Searcher searcher;
    private final Path tempXdbFile;

    public IpRegionService() {
        try {
            ClassPathResource resource = new ClassPathResource(XDB_RESOURCE);
            if (!resource.exists()) {
                throw new IllegalStateException("缺少离线 IP 地理数据文件: " + XDB_RESOURCE);
            }
            this.tempXdbFile = Files.createTempFile("westflow-ip2region-", ".xdb");
            try (InputStream inputStream = resource.getInputStream()) {
                Files.copy(inputStream, tempXdbFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            this.searcher = Searcher.newWithFileOnly(Version.IPv4, tempXdbFile.toFile());
        } catch (IOException exception) {
            throw new IllegalStateException("初始化 ip2region 失败", exception);
        }
    }

    /**
     * 解析客户端 IP 的离线地理归属，查不到时回落为“未知”。
     */
    public String resolve(String clientIp) {
        String ip = normalize(clientIp);
        if (ip.isBlank()) {
            return "未知";
        }
        String localRegion = resolveLocalRegion(ip);
        if (localRegion != null) {
            return localRegion;
        }
        try {
            return formatRegion(searcher.search(ip));
        } catch (Exception exception) {
            log.debug("ip2region resolve failed for ip={}", ip, exception);
            return "未知";
        }
    }

    @PreDestroy
    public void close() {
        try {
            searcher.close();
        } catch (Exception ignored) {
            // 释放资源时不影响 JVM 退出。
        }
        try {
            Files.deleteIfExists(tempXdbFile);
        } catch (IOException ignored) {
            // 临时文件删除失败不影响主流程。
        }
    }

    private String normalize(String clientIp) {
        if (clientIp == null) {
            return "";
        }
        return clientIp.trim();
    }

    private String resolveLocalRegion(String ip) {
        if ("localhost".equalsIgnoreCase(ip)) {
            return "本机";
        }
        try {
            InetAddress address = InetAddress.getByName(ip);
            if (address.isLoopbackAddress() || address.isAnyLocalAddress()) {
                return "本机";
            }
            if (address.isSiteLocalAddress() || address.isLinkLocalAddress()) {
                return "内网";
            }
            return null;
        } catch (UnknownHostException exception) {
            return null;
        }
    }

    private String formatRegion(String region) {
        if (region == null || region.isBlank()) {
            return "未知";
        }
        List<String> parts = List.of(region.split("\\|"));
        String formatted = parts.stream()
                .map(String::trim)
                .filter(part -> !part.isBlank() && !"0".equals(part))
                .reduce((left, right) -> left + " " + right)
                .orElse("");
        return formatted.isBlank() ? "未知" : formatted;
    }
}
