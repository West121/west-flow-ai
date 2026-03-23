package com.westflow.system.monitor;

import cn.dev33.satoken.stp.StpUtil;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.notification.mapper.NotificationChannelMapper;
import com.westflow.notification.mapper.NotificationLogMapper;
import com.westflow.notification.model.NotificationChannelRecord;
import com.westflow.notification.service.NotificationChannelService;
import com.westflow.system.monitor.api.response.NotificationChannelHealthDetailResponse;
import com.westflow.system.monitor.mapper.OrchestratorScanRecordMapper;
import com.westflow.system.monitor.mapper.TriggerExecutionRecordMapper;
import com.westflow.system.monitor.service.SystemMonitorService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemMonitorServiceTest {

    @Mock
    private OrchestratorScanRecordMapper orchestratorScanRecordMapper;

    @Mock
    private TriggerExecutionRecordMapper triggerExecutionRecordMapper;

    @Mock
    private NotificationChannelMapper notificationChannelMapper;

    @Mock
    private NotificationLogMapper notificationLogMapper;

    @Mock
    private NotificationChannelService notificationChannelService;

    @Mock
    private IdentityAuthService identityAuthService;

    @Test
    void shouldDelegateChannelHealthRecheckToNotificationDiagnosticService() {
        SystemMonitorService service = new SystemMonitorService(
                orchestratorScanRecordMapper,
                triggerExecutionRecordMapper,
                notificationChannelMapper,
                notificationLogMapper,
                notificationChannelService,
                identityAuthService
        );
        NotificationChannelRecord channel = new NotificationChannelRecord(
                "chn_001",
                "mail_main",
                "EMAIL",
                "邮件渠道",
                true,
                false,
                java.util.Map.of(
                        "endpoint", "smtp://127.0.0.1",
                        "smtpHost", "127.0.0.1",
                        "smtpPort", "25",
                        "fromAddress", "ops@westflow.cn"
                ),
                "监控测试",
                Instant.parse("2026-03-22T09:00:00Z"),
                Instant.parse("2026-03-23T02:21:00Z"),
                Instant.parse("2026-03-23T02:20:30Z")
        );
        when(identityAuthService.canAccessPhase2SystemManagement("admin")).thenReturn(true);
        when(notificationChannelMapper.selectById("chn_001")).thenReturn(channel);
        when(notificationChannelService.diagnostic("chn_001")).thenReturn(null);
        when(notificationLogMapper.selectAll()).thenReturn(List.of(
                new com.westflow.notification.model.NotificationLogRecord(
                        "log_001",
                        "chn_001",
                        "mail_main",
                        "EMAIL",
                        "ops",
                        "标题",
                        "内容",
                        "EmailHttpNotificationProvider",
                        false,
                        "FAILED",
                        "发送失败",
                        java.util.Map.of("code", "SMTP_TIMEOUT"),
                        Instant.parse("2026-03-23T02:20:30Z")
                )
        ));

        try (MockedStatic<StpUtil> mockedStpUtil = mockStatic(StpUtil.class)) {
            mockedStpUtil.when(StpUtil::getLoginIdAsString).thenReturn("admin");

            NotificationChannelHealthDetailResponse result = service.recheckNotificationChannelHealth("chn_001");

            assertThat(result.channelId()).isEqualTo("chn_001");
            assertThat(result.channelCode()).isEqualTo("mail_main");
            assertThat(result.status()).isEqualTo("ENABLED");
            assertThat(result.latestStatus()).isEqualTo("FAILED");
            assertThat(result.latestResponseMessage()).isEqualTo("发送失败");
        }

        verify(identityAuthService, times(2)).canAccessPhase2SystemManagement("admin");
        verify(notificationChannelMapper).selectById("chn_001");
        verify(notificationLogMapper).selectAll();
        verify(notificationChannelService).diagnostic("chn_001");
    }
}
