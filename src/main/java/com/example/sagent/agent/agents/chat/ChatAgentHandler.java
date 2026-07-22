package com.example.sagent.agent.agents.chat;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.base.model.AgentType;
import com.example.sagent.agent.base.model.HandlerResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

@Component
public class ChatAgentHandler implements AgentHandler {

    private final ChatClient chatClient;

    public ChatAgentHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();
    }

    @Override
    public AgentType type() {
        return AgentType.CHAT;
    }

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
