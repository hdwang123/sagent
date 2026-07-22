package com.example.sagent.agent.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 会话历史管理类
 * 负责格式化和处理会话历史记录
 */
@Component
public class ConversationHistory {

    private final ChatMemory chatMemory;

    /**
     * 构造函数
     *
     * @param chatMemory 聊天记忆实例
     */
    public ConversationHistory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    /**
     * 格式化会话历史
     *
     * @param conversationId 会话ID
     * @return 格式化后的会话历史字符串
     */
    public String format(String conversationId) {
        return chatMemory.get(conversationId).stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

    /**
     * 生成检索查询
     * 将最近的用户消息与当前消息合并，用于RAG检索
     *
     * @param conversationId 会话ID
     * @param currentMessage 当前消息
     * @return 合并后的检索查询字符串
     */
    public String retrievalQuery(String conversationId, String currentMessage) {
        List<String> previousUserMessages = chatMemory.get(conversationId).stream()
                .filter(message -> message.getMessageType() == MessageType.USER)
                .map(Message::getText)
                .toList();
        if (previousUserMessages.isEmpty()) {
            return currentMessage;
        }

        int startIndex = Math.max(0, previousUserMessages.size() - 2);
        List<String> recentUserMessages = previousUserMessages.subList(
                startIndex,
                previousUserMessages.size()
        );
        return String.join("\n", recentUserMessages) + "\n" + currentMessage;
    }

    /**
     * 格式化单条消息
     *
     * @param message 消息对象
     * @return 格式化后的消息字符串
     */
    private String formatMessage(Message message) {
        String role = switch (message.getMessageType()) {
            case USER -> "用户";
            case ASSISTANT -> "助手";
            case SYSTEM -> "系统";
            case TOOL -> "工具";
        };
        return role + "：" + message.getText();
    }
}