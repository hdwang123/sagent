package com.example.sagent.agent.core;

import com.example.sagent.agent.approval.UserIdResolver;
import com.example.sagent.agent.model.AgentResponse;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.RouteDecision;
import com.example.sagent.agent.routing.MessageClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * AgentService 单元测试
 * <p>
 * 覆盖消息路由核心逻辑：正常路由、处理器未注册降级为 CHAT、
 * 全部处理器缺失时返回错误响应、处理器类型不匹配时的降级执行。
 */
class AgentServiceTest {

    private MessageClassifier classifier;
    private HandlerRegistry handlerRegistry;
    private AgentService agentService;

    @BeforeEach
    void setUp() {
        classifier = mock(MessageClassifier.class);
        handlerRegistry = mock(HandlerRegistry.class);
        agentService = new AgentService(classifier, handlerRegistry, mock(UserIdResolver.class));
    }

    // === 正常路由 ===

    @Test
    void ask_normalRouting_returnsHandlerResult() {
        RouteDecision decision = new RouteDecision(AgentType.GSKILL, "数据查询");
        when(classifier.classify("conv-1", "查产品")).thenReturn(decision);

        AgentHandler gskillHandler = mock(AgentHandler.class);
        when(handlerRegistry.getOrDefault(AgentType.GSKILL, null)).thenReturn(gskillHandler);
        when(handlerRegistry.get(AgentType.GSKILL)).thenReturn(gskillHandler);
        when(gskillHandler.handle("conv-1", "查产品"))
                .thenReturn(new HandlerResult("找到3个产品", List.of(), 200));

        AgentResponse response = agentService.ask("conv-1", "查产品");

        assertThat(response.conversationId()).isEqualTo("conv-1");
        assertThat(response.answer()).isEqualTo("找到3个产品");
        assertThat(response.type()).isEqualTo(AgentType.GSKILL);
        assertThat(response.routeReason()).isEqualTo("数据查询");
        assertThat(response.code()).isEqualTo(200);
    }

    @Test
    void ask_chatRouting_returnsAnswer() {
        RouteDecision decision = new RouteDecision(AgentType.CHAT, "闲聊");
        when(classifier.classify("conv-1", "你好")).thenReturn(decision);

        AgentHandler chatHandler = mock(AgentHandler.class);
        when(handlerRegistry.getOrDefault(eq(AgentType.CHAT), any())).thenReturn(chatHandler);
        when(handlerRegistry.get(AgentType.CHAT)).thenReturn(chatHandler);
        when(chatHandler.handle("conv-1", "你好"))
                .thenReturn(new HandlerResult("你好！"));

        AgentResponse response = agentService.ask("conv-1", "你好");

        assertThat(response.type()).isEqualTo(AgentType.CHAT);
        assertThat(response.code()).isEqualTo(200);
    }

    // === 处理器未注册，降级为 CHAT ===

    @Test
    void ask_handlerNotRegistered_fallbackToChat() {
        RouteDecision decision = new RouteDecision(AgentType.MCP, "外部服务");
        when(classifier.classify("conv-1", "查天气")).thenReturn(decision);

        // MCP 未注册，降级到 CHAT
        AgentHandler chatHandler = mock(AgentHandler.class);
        when(handlerRegistry.getOrDefault(AgentType.MCP, chatHandler)).thenReturn(chatHandler);
        when(handlerRegistry.get(AgentType.CHAT)).thenReturn(chatHandler);
        when(chatHandler.handle("conv-1", "查天气"))
                .thenReturn(new HandlerResult("暂不支持"));

        AgentResponse response = agentService.ask("conv-1", "查天气");

        // 降级后仍执行，type 保持原始分类结果
        assertThat(response.type()).isEqualTo(AgentType.MCP);
        assertThat(response.answer()).isEqualTo("暂不支持");
    }

    // === 无任何处理器（含 CHAT 也未注册） ===

    @Test
    void ask_noHandlersAtAll_returnsErrorResponse() {
        RouteDecision decision = new RouteDecision(AgentType.CHAT, "闲聊");
        when(classifier.classify("conv-1", "你好")).thenReturn(decision);
        when(handlerRegistry.getOrDefault(AgentType.CHAT, null)).thenReturn(null);
        when(handlerRegistry.get(AgentType.CHAT)).thenReturn(null);

        AgentResponse response = agentService.ask("conv-1", "你好");

        assertThat(response.code()).isEqualTo(500);
        assertThat(response.answer()).contains("系统暂无可用的处理器");
    }

    // === 分类异常降级 ===

    @Test
    void ask_classifierThrowsException_handledByClassifier() {
        // MessageClassifier 内部已有 try-catch 降级为 CHAT，
        // AgentService 不直接处理分类异常，这里验证 classifier 降级后的正常流程
        RouteDecision fallback = new RouteDecision(AgentType.CHAT, "分类降级");
        when(classifier.classify("conv-1", "测试")).thenReturn(fallback);

        AgentHandler chatHandler = mock(AgentHandler.class);
        when(handlerRegistry.getOrDefault(eq(AgentType.CHAT), any())).thenReturn(chatHandler);
        when(handlerRegistry.get(AgentType.CHAT)).thenReturn(chatHandler);
        when(chatHandler.handle("conv-1", "测试"))
                .thenReturn(new HandlerResult("回复"));

        AgentResponse response = agentService.ask("conv-1", "测试");

        assertThat(response.type()).isEqualTo(AgentType.CHAT);
        assertThat(response.routeReason()).isEqualTo("分类降级");
    }

    // === code 透传 ===

    @Test
    void ask_handlerReturnsErrorCode_propagatesToResponse() {
        RouteDecision decision = new RouteDecision(AgentType.SKILL, "文档操作");
        when(classifier.classify("conv-1", "读取文件")).thenReturn(decision);

        AgentHandler skillHandler = mock(AgentHandler.class);
        when(handlerRegistry.getOrDefault(AgentType.SKILL, null)).thenReturn(skillHandler);
        when(handlerRegistry.get(AgentType.SKILL)).thenReturn(skillHandler);
        when(skillHandler.handle("conv-1", "读取文件"))
                .thenReturn(new HandlerResult("文件不存在", List.of(), 404));

        AgentResponse response = agentService.ask("conv-1", "读取文件");

        assertThat(response.code()).isEqualTo(404);
    }
}
