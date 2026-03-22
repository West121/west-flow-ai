package com.westflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
/**
 * 应用启动入口，负责拉起整个审批平台的 Spring Boot 容器。
 */
public class WestFlowApplication {

    /**
     * 启动应用。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WestFlowApplication.class, args);
    }
}
