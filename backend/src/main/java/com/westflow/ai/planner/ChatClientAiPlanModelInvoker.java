package com.westflow.ai.planner;

import org.springframework.ai.chat.client.ChatClient;

/**
 * 基于 Spring AI ChatClient 的计划模型调用器。
 */
public class ChatClientAiPlanModelInvoker implements AiPlanModelInvoker {

    private final ChatClient chatClient;

    public ChatClientAiPlanModelInvoker(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @Override
    public String invoke(String prompt) {
        if (chatClient == null) {
            return "";
        }
        return chatClient.prompt()
                .user(prompt)
                .call()
                .content();
    }
}
