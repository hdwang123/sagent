package com.example.sagent.agent.memory;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天记忆配置类
 * 配置会话记忆相关的Bean
 */
@Configuration
public class ChatMemoryConfiguration {

    /**
     * 最大消息数
     */
    static final int MAX_MESSAGES = 20;

    /**
     * 创建聊天记忆Bean
     *
     * @return ChatMemory实例
     */
    @Bean
    ChatMemory chatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(MAX_MESSAGES)
                .build();
    }

    /**
     * 创建消息聊天记忆顾问Bean
     *
     * @param chatMemory 聊天记忆实例
     * @return MessageChatMemoryAdvisor实例
     */
    @Bean
    MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }
}