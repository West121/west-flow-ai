package com.westflow.common.api;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RequestContextTest {

    @Test
    void shouldResolveClientIpFromForwardedHeader() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");

        assertThat(RequestContext.clientIp(request)).isEqualTo("10.0.0.1");
    }

    @Test
    void shouldFallbackToRemoteAddrWhenForwardedHeaderMissing() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertThat(RequestContext.clientIp(request)).isEqualTo("127.0.0.1");
    }
}
