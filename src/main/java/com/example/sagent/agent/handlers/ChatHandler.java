package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

/**
 * 聊天处理器
 * 处理普通聊天消息，包括闲聊、写作、翻译、总结等
 */
@Component
public class ChatHandler implements AgentHandler {

    private final ChatClient chatClient;

    /**
     * 构造函数
     *
     * @param chatClientBuilder ChatClient构建器
     * @param memoryAdvisor     消息聊天记忆顾问
     */
    public ChatHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    /**
     * 获取处理器类型
     *
     * @return AgentType.CHAT
     */
    @Override
    public AgentType type() {
        return AgentType.CHAT;
    }

    /**
     * 处理聊天消息
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果
     */
    @Override
    public HandlerResult handle(String conversationId, String message) {
        String answer = chatClient.prompt()
                .system("你是 Sagent 助手。请准确、简洁地使用中文回答用户。")
                .user(message)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();
        return new HandlerResult(answer);
    }
}