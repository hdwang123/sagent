package com.example.sagent.agent.handlers;

import com.example.sagent.agent.audit.AuditLog;
import com.example.sagent.agent.audit.OperationType;
import com.example.sagent.agent.audit.ResourceType;
import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.cost.CostMonitorService;
import com.example.sagent.agent.cost.TokenUsageCostAdvisor;
import com.example.sagent.agent.model.AgentResult;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.ToolDescriptor;
import io.modelcontextprotocol.client.McpClient;
import io.modelcontextprotocol.client.transport.HttpClientStreamableHttpTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.mcp.SyncMcpToolCallbackProvider;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

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
    private final CostMonitorService costMonitorService;
    private volatile SyncMcpToolCallbackProvider mcpToolCallbackProvider;

    /**
     * 构造函数
     *
     * @param chatClientBuilder ChatClient构建器
     * @param toolMemoryAdvisor 工具类小窗口记忆顾问（4条消息，防止LLM复述历史数据）
     * @param mcpServerUrl      MCP Server 地址（由 mcp.server.url 配置，默认 http://localhost:8081/mcp）
     */
    public McpHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("toolChatMemoryAdvisor") MessageChatMemoryAdvisor toolMemoryAdvisor,
            @Value("${mcp.server.url}")
            String mcpServerUrl,
            CostMonitorService costMonitorService
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(toolMemoryAdvisor, new SimpleLoggerAdvisor(),
                        new TokenUsageCostAdvisor(costMonitorService))
                .build();
        this.mcpServerUrl = mcpServerUrl;
        this.costMonitorService = costMonitorService;
    }

    /**
     * 延迟初始化 MCP 客户端（双重检查锁定）。
     * <p>
     * 不在应用启动时连接 MCP Server，避免 MCP Server 未就绪导致应用启动失败。
     * 首次 MCP 请求时才建立连接并初始化；连接失败时抛出异常，由 {@link #handle} 捕获返回友好提示。
     *
     * @return 已初始化的 MCP 工具回调提供器
     */
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

    /**
     * 获取处理器类型
     *
     * @return AgentType.MCP
     */
    @Override
    public AgentType type() {
        return AgentType.MCP;
    }

    /**
     * 获取当前MCP Server暴露的所有工具描述，供MessageClassifier等组件使用。
     * 首次调用会触发MCP客户端初始化；MCP Server不可用时返回空列表（分类器降级处理）。
     *
     * @return 工具描述列表
     */
    public List<ToolDescriptor> getToolDescriptors() {
        try {
            SyncMcpToolCallbackProvider provider = getMcpToolCallbackProvider();
            return Arrays.stream(provider.getToolCallbacks())
                    .map(cb -> new ToolDescriptor() {
                        @Override
                        public String getName() {
                            return cb.getToolDefinition().name();
                        }
                        @Override
                        public String getDescription() {
                            return cb.getToolDefinition().description();
                        }
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            LOGGER.warn("MCP工具描述获取失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 处理 MCP 外部服务消息
     * <p>
     * 首次调用触发 MCP 客户端延迟初始化；连接失败时返回 code=500 友好提示，不阻塞其他功能。
     * 通过 {@code .entity(AgentResult.class)} 强制 LLM 输出结构化 JSON，由 Spring AI 自动反序列化。
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果
     */
    @AuditLog(operationType = OperationType.TOOL_CALL, resourceType = ResourceType.TOOL,
            resourceId = "MCP", operationDetail = "MCP外部工具调用（计算/天气/股票等）")
    @Override
    public HandlerResult handle(String conversationId, String message) {
        try {
            var callResponse = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .tools(getMcpToolCallbackProvider().getToolCallbacks())
                    .advisors(advisor -> advisor.param(
                                    ChatMemory.CONVERSATION_ID,
                                    conversationId
                            )
                            .param("operationType", "agent/mcp"))
                    .call();
            AgentResult result = callResponse.entity(AgentResult.class);

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
