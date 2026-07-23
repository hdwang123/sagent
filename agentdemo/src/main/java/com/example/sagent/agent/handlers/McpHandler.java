package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * MCP处理器
 * 通过MCP协议调用外部MCP Server提供的工具
 * MCP连接在首次请求时建立，而非应用启动时
 */
@Component
public class McpHandler implements AgentHandler {

    private static final String SYSTEM_PROMPT = """
            你是MCP工具执行助手，可以调用MCP服务器提供的工具完成任务。
            必须调用提供的MCP工具完成任务，不能自行编造结果。
            如果现有工具无法满足需求，请明确说明当前支持的工具范围。
            使用中文简洁回答，并说明已执行的操作。
            """;

    private final ChatClient chatClient;
    private final String mcpServerUrl;
    private volatile SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    public McpHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            @Value("${mcp.server.url}")
            String mcpServerUrl
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor, new SimpleLoggerAdvisor())
                .build();
        this.mcpServerUrl = mcpServerUrl;
    }

    private SyncMcpToolCallbackProvider getMcpToolCallbackProvider() {
        if (mcpToolCallbackProvider == null) {
            synchronized (this) {
                if (mcpToolCallbackProvider == null) {
                    var transport = HttpClientStreamableHttpTransport.builder(mcpServerUrl).build();
                    var client = McpClient.sync(transport).build();
                    client.initialize();
                    mcpToolCallbackProvider = SyncMcpToolCallbackProvider.builder()
                            .mcpClients(List.of(client))
                            .build();
                }
            }
        }
        return mcpToolCallbackProvider;
    }

    @Override
    public AgentType type() {
        return AgentType.MCP;
    }

    @Override
    public HandlerResult handle(String conversationId, String message) {
        try {
            String answer = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .tools(getMcpToolCallbackProvider().getToolCallbacks())
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            conversationId
                    ))
                    .call()
                    .content();
            return new HandlerResult(answer);
        } catch (Exception e) {
            return new HandlerResult("MCP服务连接失败: " + e.getMessage());
        }
    }
}
