package com.westflow.identity.security;

import cn.dev33.satoken.stp.StpInterface;
import com.westflow.identity.service.IdentityAuthService;
import com.westflow.system.audit.AuditLogInterceptor;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Sa-Token 和审计拦截器配置。
 */
@Configuration
public class SaTokenConfiguration implements WebMvcConfigurer {

    private final AuditLogInterceptor auditLogInterceptor;

    public SaTokenConfiguration(AuditLogInterceptor auditLogInterceptor) {
        this.auditLogInterceptor = auditLogInterceptor;
    }

    /**
     * 提供 Sa-Token 权限角色查询实现。
     */
    @Bean
    public StpInterface stpInterface(IdentityAuthService fixtureAuthService) {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return fixtureAuthService.permissionsByUserId(String.valueOf(loginId));
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return fixtureAuthService.rolesByUserId(String.valueOf(loginId));
            }
        };
    }

    /**
     * 注册审计日志拦截器。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditLogInterceptor).addPathPatterns("/api/v1/**");
    }
}
