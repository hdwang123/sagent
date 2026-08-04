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
     * 最大消息数（普通聊天）
     */
    static final int MAX_MESSAGES = 20;

    /**
     * 工具类处理器最大消息数（小窗口强制重新查数据）
     */
    static final int MAX_TOOL_MESSAGES = 4;

    /**
     * 创建聊天记忆Bean（普通聊天用）
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
     * 创建消息聊天记忆顾问Bean（普通聊天用）
     *
     * @param chatMemory 聊天记忆实例
     * @return MessageChatMemoryAdvisor实例
     */
    @Bean
    MessageChatMemoryAdvisor messageChatMemoryAdvisor(ChatMemory chatMemory) {
        return MessageChatMemoryAdvisor.builder(chatMemory).build();
    }

    /**
     * 创建工具类聊天记忆Bean（GSkill/Skill 用小窗口，防止LLM复述历史数据）
     *
     * @return ChatMemory实例（最多4条消息）
     */
    @Bean
    ChatMemory toolChatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(MAX_TOOL_MESSAGES)
                .build();
    }

    /**
     * 创建工具类消息聊天记忆顾问Bean
     *
     * @param toolChatMemory 工具类聊天记忆实例
     * @return MessageChatMemoryAdvisor实例
     */
    @Bean
    MessageChatMemoryAdvisor toolChatMemoryAdvisor(ChatMemory toolChatMemory) {
        return MessageChatMemoryAdvisor.builder(toolChatMemory).build();
    }

    /**
     * 多Agent编排专用会话记忆（独立于单Agent的chatMemory，不混用）。
     * <p>
     * 用作多Agent多轮会话的单独演示：Planner读取历史理解上下文指代，
     * MultiAgentService编排完成后写入本轮结果，形成多Agent专属的多轮闭环。
     * 与单Agent的chatMemory完全隔离，互不干扰。
     */
    @Bean
    ChatMemory multiAgentChatMemory() {
        return MessageWindowChatMemory.builder()
                .maxMessages(MAX_MESSAGES)
                .build();
    }
}