package com.example.sagent.agent.chat;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class ChatAgentHandler implements AgentHandler {

    private final ChatClient chatClient;

    public ChatAgentHandler(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Override
    public AgentType type() {
        return AgentType.CHAT;
    }

    @Override
    public HandlerResult handle(String message) {
        String answer = chatClient.prompt()
                .system("你是 Sagent 助手。请准确、简洁地使用中文回答用户。")
                .user(message)
                .call()
                .content();
        return new HandlerResult(answer);
    }
}
