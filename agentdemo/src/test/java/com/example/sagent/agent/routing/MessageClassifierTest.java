package com.example.sagent.agent.routing;

import com.example.sagent.agent.cost.CostMonitorService;
import com.example.sagent.agent.handlers.McpHandler;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.RouteDecision;
import com.example.sagent.agent.skills.ASkill;
import com.example.sagent.agent.skills.GSkill;
import com.example.sagent.agent.skills.Skill;
import com.example.sagent.agent.skills.ToolDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.api.Advisor;

import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * MessageClassifier 单元测试
 * <p>
 * 覆盖分类核心逻辑：正常分类返回、LLM异常降级CHAT、空决策降级CHAT、null类型降级CHAT。
 * 通过 mock ChatClient 链式调用隔离 LLM，只测分类/降级逻辑。
 */
class MessageClassifierTest {

    private ChatClient.Builder mockBuilder;
    private ChatClient mockClient;
    private ChatClient.ChatClientRequestSpec mockSpec;
    private ChatClient.CallResponseSpec mockCallSpec;
    private ConversationHistory mockHistory;
    private McpHandler mockMcpHandler;
    private MessageClassifier classifier;

    @BeforeEach
    void setUp() {
        mockBuilder = mock(ChatClient.Builder.class);
        mockClient = mock(ChatClient.class);
        mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        mockCallSpec = mock(ChatClient.CallResponseSpec.class);
        mockHistory = mock(ConversationHistory.class);
        mockMcpHandler = mock(McpHandler.class);
        when(mockMcpHandler.getToolDescriptors()).thenReturn(List.of());

        when(mockBuilder.build()).thenReturn(mockClient);
        when(mockClient.prompt()).thenReturn(mockSpec);
        when(mockSpec.system(anyString())).thenReturn(mockSpec);
        when(mockSpec.user(anyString())).thenReturn(mockSpec);
        when(mockSpec.advisors(any(Advisor.class))).thenReturn(mockSpec);
        when(mockSpec.advisors(any(Consumer.class))).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockCallSpec);
        when(mockHistory.format(anyString())).thenReturn("");

        classifier = new MessageClassifier(mockBuilder, mockHistory, List.of(), List.of(), List.of(), mockMcpHandler,
                mock(CostMonitorService.class));
    }

    @Test
    void classify_normal_returnsDecision() {
        RouteDecision decision = new RouteDecision(AgentType.GSKILL, "数据查询");
        when(mockCallSpec.entity(eq(RouteDecision.class), any(Consumer.class))).thenReturn(decision);

        RouteDecision result = classifier.classify("conv-1", "查产品");

        assertThat(result.type()).isEqualTo(AgentType.GSKILL);
        assertThat(result.reason()).isEqualTo("数据查询");
    }

    @Test
    void classify_llmThrowsException_fallbackToChat() {
        when(mockClient.prompt()).thenThrow(new RuntimeException("LLM unavailable"));

        RouteDecision result = classifier.classify("conv-1", "测试");

        assertThat(result.type()).isEqualTo(AgentType.CHAT);
        assertThat(result.reason()).contains("兜底");
    }

    @Test
    void classify_nullDecision_fallbackToChat() {
        when(mockCallSpec.entity(eq(RouteDecision.class), any(Consumer.class))).thenReturn(null);

        RouteDecision result = classifier.classify("conv-1", "测试");

        assertThat(result.type()).isEqualTo(AgentType.CHAT);
    }

    @Test
    void classify_nullType_fallbackToChat() {
        // LLM 返回了对象但 type 字段为 null
        RouteDecision nullTypeDecision = new RouteDecision(null, "无法分类");
        when(mockCallSpec.entity(eq(RouteDecision.class), any(Consumer.class))).thenReturn(nullTypeDecision);

        RouteDecision result = classifier.classify("conv-1", "测试");

        assertThat(result.type()).isEqualTo(AgentType.CHAT);
    }

    @Test
    void classify_withHistory_includesHistoryInInput() {
        when(mockHistory.format("conv-1")).thenReturn("用户之前问了产品价格");
        RouteDecision decision = new RouteDecision(AgentType.CHAT, "闲聊");
        when(mockCallSpec.entity(eq(RouteDecision.class), any(Consumer.class))).thenReturn(decision);

        classifier.classify("conv-1", "那库存呢");

        // 验证 user() 被调用时包含了历史信息
        verify(mockSpec).user(contains("用户之前问了产品价格"));
    }

    @Test
    void classify_emptyHistory_usesMessageDirectly() {
        when(mockHistory.format("conv-1")).thenReturn("");
        RouteDecision decision = new RouteDecision(AgentType.CHAT, "闲聊");
        when(mockCallSpec.entity(eq(RouteDecision.class), any(Consumer.class))).thenReturn(decision);

        classifier.classify("conv-1", "你好");

        // 历史为空时，user() 只包含原始消息
        verify(mockSpec).user(eq("你好"));
    }

    @Test
    void classify_withSkills_buildsPromptWithToolList() {
        Skill mockSkill = mock(Skill.class);
        when(mockSkill.getName()).thenReturn("document");
        when(mockSkill.getDescription()).thenReturn("生成Markdown文档");

        // 重新构建 classifier，包含技能
        classifier = new MessageClassifier(mockBuilder, mockHistory, List.of(mockSkill), List.of(), List.of(), mockMcpHandler,
                mock(CostMonitorService.class));
        RouteDecision decision = new RouteDecision(AgentType.SKILL, "文档操作");
        when(mockCallSpec.entity(eq(RouteDecision.class), any(Consumer.class))).thenReturn(decision);

        classifier.classify("conv-1", "生成文档");

        // 验证 system prompt 包含技能描述
        verify(mockSpec).system(contains("document: 生成Markdown文档"));
    }

    @Test
    void classify_withMcpTools_buildsPromptWithDynamicMcpList() {
        // MCP 工具清单动态化：由 McpHandler 实时拉取，不再硬编码
        when(mockMcpHandler.getToolDescriptors()).thenReturn(List.of(
                new ToolDescriptor() {
                    @Override public String getName() { return "get_weather"; }
                    @Override public String getDescription() { return "获取指定城市天气"; }
                },
                new ToolDescriptor() {
                    @Override public String getName() { return "calculator"; }
                    @Override public String getDescription() { return "计算器（加减乘除）"; }
                }
        ));
        RouteDecision decision = new RouteDecision(AgentType.MCP, "外部服务");
        when(mockCallSpec.entity(eq(RouteDecision.class), any(Consumer.class))).thenReturn(decision);

        classifier.classify("conv-1", "北京天气怎么样");

        // 验证 system prompt 包含动态获取的 MCP 工具清单（替代硬编码）
        verify(mockSpec).system(contains("get_weather: 获取指定城市天气"));
        verify(mockSpec).system(contains("calculator: 计算器（加减乘除）"));
    }
}
