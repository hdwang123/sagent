package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.cost.CostMonitorService;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 聊天处理器
 * 处理普通聊天消息，包括闲聊、写作、翻译、总结等
 */
@Component
public class ChatHandler implements AgentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ChatHandler.class);

    private final ChatClient chatClient;
    private final CostMonitorService costMonitorService;

    /**
     * 构造函数
     *
     * @param chatClientBuilder ChatClient构建器
     * @param memoryAdvisor     消息聊天记忆顾问
     */
    public ChatHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("messageChatMemoryAdvisor") MessageChatMemoryAdvisor memoryAdvisor,
            CostMonitorService costMonitorService
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();
        this.costMonitorService = costMonitorService;
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
        try {
            var callResponse = chatClient.prompt()
                    .system("你是 Sagent 助手。请准确、简洁地使用中文回答用户。")
                    .user(message)
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            conversationId
                    ))
                    .call();
            String answer = callResponse.content();
            costMonitorService.saveCostRecord(conversationId, "CHAT", callResponse.chatResponse());
            return new HandlerResult(answer);
        } catch (Exception e) {
            LOGGER.error("ChatHandler处理失败", e);
            return new HandlerResult("聊天处理失败：" + e.getMessage(), List.of(), HandlerResult.CODE_ERROR);
        }
    }
}