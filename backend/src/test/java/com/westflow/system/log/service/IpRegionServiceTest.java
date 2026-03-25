package com.westflow.system.log.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IpRegionServiceTest {

    @Test
    void shouldResolveKnownLocalIpAndFallbackForInvalidIp() {
        IpRegionService service = new IpRegionService();
        try {
            assertThat(service.resolve("127.0.0.1")).isNotBlank();
            assertThat(service.resolve("invalid-ip")).isEqualTo("未知");
        } finally {
            service.close();
        }
    }
}
