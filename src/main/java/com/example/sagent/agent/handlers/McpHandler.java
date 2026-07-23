package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.client.SyncMcpToolCallbackProvider;
import org.springframework.stereotype.Component;

/**
 * MCP处理器
 * 处理通过外部MCP服务器调用工具的消息
 */
@Component
public class McpHandler implements AgentHandler {

    private static final String SYSTEM_PROMPT = """
            你是MCP工具执行助手，可以调用外部MCP服务器提供的工具完成任务。
            必须调用提供的MCP工具完成任务，不能自行编造结果。
            如果现有工具无法满足需求，请明确说明当前支持的工具范围。
            使用中文简洁回答，并说明已执行的操作。
            """;

    private final ChatClient chatClient;
    private final SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    public McpHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            SyncMcpToolCallbackProvider mcpToolCallbackProvider
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor, new SimpleLoggerAdvisor())
                .build();
        this.mcpToolCallbackProvider = mcpToolCallbackProvider;
    }

    @Override
    public AgentType type() {
        return AgentType.MCP;
    }

    @Override
    public HandlerResult handle(String conversationId, String message) {
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .tools(mcpToolCallbackProvider.getCallbacks())
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();

        return new HandlerResult(answer);
    }
}