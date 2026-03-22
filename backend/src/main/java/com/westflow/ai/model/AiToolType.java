package com.westflow.ai.model;

/**
 * AI 工具类型。
 */
public enum AiToolType {
    /**
     * 读取型工具，允许直接执行。
     */
    READ,
    /**
     * 写入型工具，需要二次确认。
     */
    WRITE
}
