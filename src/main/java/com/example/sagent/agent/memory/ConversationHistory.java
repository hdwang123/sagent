package com.example.sagent.agent.memory;

import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class ConversationHistory {

    private final ChatMemory chatMemory;

    public ConversationHistory(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public String format(String conversationId) {
        return chatMemory.get(conversationId).stream()
                .map(this::formatMessage)
                .collect(Collectors.joining("\n"));
    }

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
