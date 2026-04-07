package com.westflow.ai.planner;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * 基于 Spring AI ChatClient 的计划模型调用器。
 */
public class ChatClientAiPlanModelInvoker implements AiPlanModelInvoker {

    private final ChatClient chatClient;
    private final String modelName;

    public ChatClientAiPlanModelInvoker(ChatClient chatClient) {
        this(chatClient, "");
    }

    public ChatClientAiPlanModelInvoker(ChatClient chatClient, String modelName) {
        this.chatClient = chatClient;
        this.modelName = modelName == null ? "" : modelName.trim();
    }

    @Override
    public String invoke(String prompt) {
        if (chatClient == null) {
            return "";
        }
        ChatClient.ChatClientRequestSpec requestSpec = chatClient.prompt();
        if (!modelName.isBlank()) {
            requestSpec = requestSpec.options(OpenAiChatOptions.builder().model(modelName).build());
        }
        return requestSpec.user(prompt)
                .call()
                .content();
    }
}
