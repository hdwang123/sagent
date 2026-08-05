package com.example.sagent.agent.handlers;

import com.example.sagent.agent.approval.UserIdResolver;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.ASkill;
import com.example.sagent.agent.skills.GSkill;
import com.example.sagent.agent.skills.Skill;
import com.example.sagent.agent.tools.VectorKnowledgeRetriever;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.model.ChatModel;
import tools.jackson.databind.ObjectMapper;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Handler 单元测试
 * <p>
 * 覆盖 6 个 Handler 的 type() 返回值和异常处理逻辑。
 * 异常处理测试通过 mock ChatClient.prompt() 抛出异常，验证各 Handler 的 try-catch 兜底。
 */
class HandlerTest {

    private ChatClient.Builder mockBuilder;
    private ChatClient mockClient;
    private MessageChatMemoryAdvisor mockAdvisor;

    @BeforeEach
    void setUp() {
        mockBuilder = mock(ChatClient.Builder.class);
        mockClient = mock(ChatClient.class);
        mockAdvisor = mock(MessageChatMemoryAdvisor.class);

        lenient().when(mockBuilder.defaultAdvisors(any(Advisor[].class))).thenReturn(mockBuilder);
        lenient().when(mockBuilder.build()).thenReturn(mockClient);
    }

    // === ChatHandler ===

    @Test
    void chatHandler_type() {
        ChatHandler handler = new ChatHandler(mockBuilder, mockAdvisor);
        assertThat(handler.type()).isEqualTo(AgentType.CHAT);
    }

    @Test
    void chatHandler_exception_returnsErrorResult() {
        when(mockClient.prompt()).thenThrow(new RuntimeException("LLM unavailable"));
        ChatHandler handler = new ChatHandler(mockBuilder, mockAdvisor);

        HandlerResult result = handler.handle("conv-1", "你好");

        assertThat(result.code()).isEqualTo(HandlerResult.CODE_ERROR);
        assertThat(result.answer()).contains("聊天处理失败");
    }

    // === SkillHandler ===

    @Test
    void skillHandler_type() {
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        SkillHandler handler = new SkillHandler(mockBuilder, mockAdvisor, List.of(), mockMapper);
        assertThat(handler.type()).isEqualTo(AgentType.SKILL);
    }

    @Test
    void skillHandler_exception_returnsErrorResult() {
        when(mockClient.prompt()).thenThrow(new RuntimeException("LLM unavailable"));
        ObjectMapper mockMapper = mock(ObjectMapper.class);
        SkillHandler handler = new SkillHandler(mockBuilder, mockAdvisor, List.of(), mockMapper);

        HandlerResult result = handler.handle("conv-1", "生成文档");

        assertThat(result.code()).isEqualTo(HandlerResult.CODE_ERROR);
        assertThat(result.answer()).contains("技能执行失败");
    }

    // === GSkillHandler ===

    @Test
    void gskillHandler_type() {
        GSkillHandler handler = new GSkillHandler(mockBuilder, mockAdvisor, List.of());
        assertThat(handler.type()).isEqualTo(AgentType.GSKILL);
    }

    @Test
    void gskillHandler_exception_returnsErrorResult() {
        when(mockClient.prompt()).thenThrow(new RuntimeException("LLM unavailable"));
        GSkillHandler handler = new GSkillHandler(mockBuilder, mockAdvisor, List.of());

        HandlerResult result = handler.handle("conv-1", "查产品");

        assertThat(result.code()).isEqualTo(HandlerResult.CODE_ERROR);
        assertThat(result.answer()).contains("通用技能执行失败");
    }

    // === ASkillHandler ===

    @Test
    void askillHandler_type() {
        UserIdResolver mockResolver = mock(UserIdResolver.class);
        ASkillHandler handler = new ASkillHandler(mockBuilder, mockAdvisor, List.of(), mockResolver);
        assertThat(handler.type()).isEqualTo(AgentType.ASKILL);
    }

    @Test
    void askillHandler_exception_returnsErrorResult() {
        when(mockClient.prompt()).thenThrow(new RuntimeException("LLM unavailable"));
        UserIdResolver mockResolver = mock(UserIdResolver.class);
        ASkillHandler handler = new ASkillHandler(mockBuilder, mockAdvisor, List.of(), mockResolver);

        HandlerResult result = handler.handle("conv-1", "删除产品");

        assertThat(result.code()).isEqualTo(HandlerResult.CODE_ERROR);
        assertThat(result.answer()).contains("审批技能执行失败");
    }

    // === McpHandler ===

    @Test
    void mcpHandler_type() {
        McpHandler handler = new McpHandler(mockBuilder, mockAdvisor, "http://localhost:8081/mcp");
        assertThat(handler.type()).isEqualTo(AgentType.MCP);
    }

    @Test
    void mcpHandler_exception_returnsErrorResult() {
        when(mockClient.prompt()).thenThrow(new RuntimeException("MCP connection refused"));
        McpHandler handler = new McpHandler(mockBuilder, mockAdvisor, "http://localhost:8081/mcp");

        HandlerResult result = handler.handle("conv-1", "查天气");

        assertThat(result.code()).isEqualTo(HandlerResult.CODE_ERROR);
        assertThat(result.answer()).contains("MCP服务连接失败");
    }

    // === RagHandler ===

    @Test
    void ragHandler_type() {
        VectorKnowledgeRetriever mockRetriever = mock(VectorKnowledgeRetriever.class);
        ConversationHistory mockHistory = mock(ConversationHistory.class);
        ChatModel mockChatModel = mock(ChatModel.class);
        RagHandler handler = new RagHandler(mockBuilder, mockAdvisor, mockRetriever, mockHistory, mockChatModel, 10, 3);
        assertThat(handler.type()).isEqualTo(AgentType.RAG);
    }

    @Test
    void ragHandler_retrievalError_returnsErrorResult() {
        VectorKnowledgeRetriever mockRetriever = mock(VectorKnowledgeRetriever.class);
        ConversationHistory mockHistory = mock(ConversationHistory.class);
        ChatModel mockChatModel = mock(ChatModel.class);
        when(mockHistory.retrievalQuery(anyString(), anyString()))
                .thenThrow(new RuntimeException("向量检索失败"));

        RagHandler handler = new RagHandler(mockBuilder, mockAdvisor, mockRetriever, mockHistory, mockChatModel, 10, 3);

        HandlerResult result = handler.handle("conv-1", "Sagent是什么");

        assertThat(result.code()).isEqualTo(HandlerResult.CODE_ERROR);
        assertThat(result.answer()).contains("知识库检索失败");
    }

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }
}
