package com.westflow.ai.model;

import java.util.List;

/**
 * 追加消息请求。
 */
public record AiMessageAppendRequest(
        // 文本内容。
        String content,
        // 附件引用。
        List<AiAttachmentRequest> attachments
) {
    public AiMessageAppendRequest(String content) {
        this(content, List.of());
    }

    public AiMessageAppendRequest {
        attachments = attachments == null ? List.of() : List.copyOf(attachments);
    }
}
