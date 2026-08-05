package com.example.sagent.agent.memory;

import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天记忆配置类
 * 配置会话记忆相关的Bean
 */
@Configuration
public class ChatMemoryConfiguration {

    @Bean
    ChatMemory chatMemory(
            @Value("${agent.chat-memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }

    @Bean
    MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    @Bean
    ChatMemory toolChatMemory(
            @Value("${agent.chat-memory.max-tool-messages:4}") int maxToolMessages) {
        return MessageWindowChatMemory.builder()
                .maxMessages(maxToolMessages)
                .build();
    }

    @Bean
    MessageChatMemoryAdvisor toolChatMemoryAdvisor(ChatMemory toolChatMemory) {
        return MessageChatMemoryAdvisor.builder(toolChatMemory).build();
    }

    @Bean
    ChatMemory multiAgentChatMemory(
            @Value("${agent.chat-memory.max-messages:20}") int maxMessages) {
        return MessageWindowChatMemory.builder()
                .maxMessages(maxMessages)
                .build();
    }
}