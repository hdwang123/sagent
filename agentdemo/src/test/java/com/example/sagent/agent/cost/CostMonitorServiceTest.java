package com.example.sagent.agent.cost;

import com.example.sagent.agent.approval.UserIdResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * CostMonitorService 缓存命中计费单元测试
 * <p>
 * 覆盖：输入 token 命中/未命中两档计费拆分、cacheRead 为 null 时全按未命中价、
 * cacheRead 大于输入总数时按输入总数截断（防御性校验）、费用正确落库。
 */
class CostMonitorServiceTest {

    private static final BigDecimal INPUT = BigDecimal.valueOf(0.003);        // 未命中价 /1K（高峰档）
    private static final BigDecimal OUTPUT = BigDecimal.valueOf(0.009);        // 输出价 /1K（高峰档）
    private static final BigDecimal CACHE_READ = BigDecimal.valueOf(0.0001);  // 命中价 /1K（高峰档）

    private CostRecordRepository repository;
    private UserIdResolver userIdResolver;
    private CostMonitorService service;

    @BeforeEach
    void setUp() {
        repository = mock(CostRecordRepository.class);
        ModelPricing pricing = new ModelPricing("deepseek-v4-flash", Map.of(
                "deepseek-v4-flash", new ModelPricing.Pricing(INPUT, OUTPUT, CACHE_READ)));
        userIdResolver = mock(UserIdResolver.class);
        when(userIdResolver.resolve("conv-1")).thenReturn("user-42");
        service = new CostMonitorService(repository, pricing, userIdResolver);
    }

    @Test
    void saveCostRecord_allCacheRead_chargesCacheReadPrice() {
        service.saveCostRecord("user-1", "deepseek-v4-flash", 1000L, 1000L, 0L, "agent/chat", "conv-1",
                "prompt-1", "completion-1");

        CostRecord record = capturedRecord();
        // 1000 命中 × 0.0001/1K = 0.0001，未命中为 0
        assertThat(record.getCostCny()).isEqualByComparingTo("0.0001");
        // LLM 输入输出内容一并落库
        assertThat(record.getPromptContent()).isEqualTo("prompt-1");
        assertThat(record.getCompletionContent()).isEqualTo("completion-1");
    }

    @Test
    void saveCostRecord_partialCacheRead_splitsPricing() {
        // 400 命中 + 600 未命中 + 500 输出
        service.saveCostRecord("user-1", "deepseek-v4-flash", 400L, 1000L, 500L, "agent/chat", "conv-1",
                "prompt-2", "completion-2");

        CostRecord record = capturedRecord();
        // 400×0.0001/1K + 600×0.003/1K + 500×0.009/1K = 0.00004 + 0.0018 + 0.0045
        assertThat(record.getCostCny()).isEqualByComparingTo("0.00634");
        assertThat(record.getInputTokens()).isEqualTo(1000L);
        assertThat(record.getOutputTokens()).isEqualTo(500L);
        assertThat(record.getTotalTokens()).isEqualTo(1500L);
        assertThat(record.getCacheReadInputTokens()).isEqualTo(400L);
    }

    @Test
    void saveCostRecord_nullCacheRead_chargesFullMissPrice() {
        service.saveCostRecord("user-1", "deepseek-v4-flash", null, 1000L, 0L, "agent/chat", "conv-1",
                null, null);

        CostRecord record = capturedRecord();
        // 无命中信息时全按未命中价：1000×0.003/1K
        assertThat(record.getCostCny()).isEqualByComparingTo("0.003");
        // 内容可为 null
        assertThat(record.getPromptContent()).isNull();
        assertThat(record.getCompletionContent()).isNull();
    }

    @Test
    void saveCostRecord_cacheReadExceedsInput_clampsToInput() {
        // 防御性校验：命中数超过输入总数时按输入总数截断
        service.saveCostRecord("user-1", "deepseek-v4-flash", 1000L, 500L, 0L, "agent/chat", "conv-1",
                "prompt-4", "completion-4");

        CostRecord record = capturedRecord();
        // 500 命中 × 0.0001/1K = 0.00005
        assertThat(record.getCostCny()).isEqualByComparingTo("0.00005");
    }

    @Test
    void saveCostRecord_fromRequestResponse_extractsPromptAndCompletion() {
        // 便捷方法：从 ChatClientRequest/ChatClientResponse 提取输入输出内容
        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.prompt()).thenReturn(new Prompt("用户问题"));

        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);
        when(metadata.getModel()).thenReturn("deepseek-v4-flash");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(chatResponse.getResults()).thenReturn(java.util.List.of(generation));
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("模型回答");

        ChatClientResponse response = mock(ChatClientResponse.class);
        when(response.chatResponse()).thenReturn(chatResponse);

        service.saveCostRecord("conv-1", "agent/chat", request, response);

        CostRecord record = capturedRecord();
        assertThat(record.getOperationType()).isEqualTo("agent/chat");
        // userId 由 UserIdResolver 从会话ID解析，而非直接落会话ID
        assertThat(record.getUserId()).isEqualTo("user-42");
        assertThat(record.getPromptContent()).isEqualTo("用户问题");
        assertThat(record.getCompletionContent()).isEqualTo("模型回答");
        assertThat(record.getInputTokens()).isEqualTo(10L);
        assertThat(record.getOutputTokens()).isEqualTo(5L);
    }

    @Test
    void extractCompletion_mergesAllGenerationsAndToolCalls() {
        // 多 generation：第一个含文本，第二个纯工具调用，输出应包含两者的完整内容
        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.prompt()).thenReturn(new Prompt("查询天气"));

        AssistantMessage textMessage = mock(AssistantMessage.class);
        when(textMessage.getText()).thenReturn("好的，我来查询。");
        when(textMessage.getToolCalls()).thenReturn(null);
        Generation gen1 = mock(Generation.class);
        when(gen1.getOutput()).thenReturn(textMessage);

        AssistantMessage toolMessage = mock(AssistantMessage.class);
        when(toolMessage.getText()).thenReturn(null);
        when(toolMessage.getToolCalls()).thenReturn(java.util.List.of(
                new AssistantMessage.ToolCall("1", "function", "get_weather", "{\"city\":\"上海\"}")));
        Generation gen2 = mock(Generation.class);
        when(gen2.getOutput()).thenReturn(toolMessage);

        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(metadata.getModel()).thenReturn("deepseek-v4-flash");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(chatResponse.getResults()).thenReturn(java.util.List.of(gen1, gen2));

        ChatClientResponse response = mock(ChatClientResponse.class);
        when(response.chatResponse()).thenReturn(chatResponse);

        service.saveCostRecord("conv-1", "agent/skill", request, response);

        CostRecord record = capturedRecord();
        assertThat(record.getCompletionContent())
                .contains("好的，我来查询。")
                .contains("[工具调用] get_weather({\"city\":\"上海\"})");
    }

    @Test
    void saveCostRecord_appendsEntityFormatHintToPrompt() {
        // entity() 的 JSON 格式提示在 context 的 OUTPUT_FORMAT 中，应追加到记录的输入内容
        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.prompt()).thenReturn(new Prompt("查询产品"));
        when(request.context()).thenReturn(Map.of(
                ChatClientAttributes.OUTPUT_FORMAT.getKey(),
                "你的输出必须为合法JSON，字段为 {type, reason}"));

        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);
        when(metadata.getModel()).thenReturn("deepseek-v4-flash");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(chatResponse.getResults()).thenReturn(java.util.List.of(generation));
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("{\"type\":\"CHAT\"}");

        ChatClientResponse response = mock(ChatClientResponse.class);
        when(response.chatResponse()).thenReturn(chatResponse);

        service.saveCostRecord("conv-1", "routing/classifier", request, response);

        CostRecord record = capturedRecord();
        assertThat(record.getPromptContent())
                .contains("查询产品")
                .contains("【entity 格式提示】")
                .contains("你的输出必须为合法JSON");
    }

    @Test
    void saveCostRecord_appendsToolDefinitionsToPrompt() {
        // 工具定义在 prompt options（ToolCallingChatOptions）中，应追加到记录的输入内容
        ToolCallingChatOptions options = mock(ToolCallingChatOptions.class);
        ToolCallback tool = mock(ToolCallback.class);
        ToolDefinition def = mock(ToolDefinition.class);
        when(def.name()).thenReturn("get_product");
        when(def.description()).thenReturn("查询产品信息");
        when(def.inputSchema()).thenReturn("{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}");
        when(tool.getToolDefinition()).thenReturn(def);
        when(options.getToolCallbacks()).thenReturn(java.util.List.of(tool));

        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.prompt()).thenReturn(new Prompt("查询产品", options));
        when(request.context()).thenReturn(Map.of());

        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        Generation generation = mock(Generation.class);
        AssistantMessage assistantMessage = mock(AssistantMessage.class);
        when(metadata.getModel()).thenReturn("deepseek-v4-flash");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(chatResponse.getMetadata()).thenReturn(metadata);
        when(chatResponse.getResults()).thenReturn(java.util.List.of(generation));
        when(generation.getOutput()).thenReturn(assistantMessage);
        when(assistantMessage.getText()).thenReturn("{\"code\":200}");

        ChatClientResponse response = mock(ChatClientResponse.class);
        when(response.chatResponse()).thenReturn(chatResponse);

        service.saveCostRecord("conv-1", "agent/gskill", request, response);

        CostRecord record = capturedRecord();
        assertThat(record.getPromptContent())
                .contains("查询产品")
                .contains("【工具定义】")
                .contains("- get_product: 查询产品信息")
                .contains("schema: {\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\"}}}");
    }

    private CostRecord capturedRecord() {
        ArgumentCaptor<CostRecord> captor = ArgumentCaptor.forClass(CostRecord.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
