package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentResult;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.beans.factory.annotation.Qualifier;
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

    private static final Logger LOGGER = LoggerFactory.getLogger(McpHandler.class);

    private static final String SYSTEM_PROMPT = """
            你是MCP工具执行助手，可以调用MCP服务器提供的工具完成任务。
            必须调用提供的MCP工具完成任务，不能自行编造结果。
            如果现有工具无法满足需求，请明确说明当前支持的工具范围。

            【输出要求】调用工具并汇总结果后，输出结构化结果（code + content），其中 code 取值约定：
            - 200：任务执行成功
            - 404：数据或资源不存在
            - 400：业务校验失败（如参数非法）
            - 500：工具执行出现技术错误
            如果工具返回的 code 为 400/404/500，必须如实反映到最终 code 并说明失败原因，不能伪造成功。

            【content 编写规则】content 是直接展示给用户的回答文本：
            - 用中文自然语言整理工具返回的数据，严禁将原始 JSON 或转义字符串原样放入 content
            - 错误示例：{"code":200,"content":"{\"id\":3,\"name\":\"iPhone 15\"}"}
            - 正确示例：{"code":200,"content":"北京市当前天气：晴，25℃，空气质量良。"}
            - 简洁明了，说明已执行的操作和关键结果。
            """;

    private final ChatClient chatClient;
    private final String mcpServerUrl;
    private volatile SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    public McpHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("toolChatMemoryAdvisor") MessageChatMemoryAdvisor toolMemoryAdvisor,
            @Value("${mcp.server.url}")
            String mcpServerUrl
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(toolMemoryAdvisor, new SimpleLoggerAdvisor())
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
            AgentResult result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .tools(getMcpToolCallbackProvider().getToolCallbacks())
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            conversationId
                    ))
                    .call()
                    .entity(AgentResult.class);

            if (result == null) {
                return new HandlerResult("", List.of(), HandlerResult.CODE_SUCCESS);
            }
            return new HandlerResult(result.content(), List.of(), result.code());
        } catch (Exception e) {
            LOGGER.warn("MCP调用失败: {}", e.getMessage());
            return new HandlerResult("MCP服务连接失败: " + e.getMessage(), List.of(), HandlerResult.CODE_ERROR);
        }
    }
}
