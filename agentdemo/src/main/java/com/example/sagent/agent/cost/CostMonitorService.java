package com.example.sagent.agent.cost;

import com.example.sagent.agent.approval.UserIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成本监控服务
 * 异步记录 LLM 调用的 token 消耗和费用
 */
@Service
public class CostMonitorService {

    private static final Logger LOGGER = LoggerFactory.getLogger(CostMonitorService.class);

    private final CostRecordRepository costRecordRepository;
    private final ModelPricing modelPricing;
    private final UserIdResolver userIdResolver;

    public CostMonitorService(CostRecordRepository costRecordRepository, ModelPricing modelPricing,
                              UserIdResolver userIdResolver) {
        this.costRecordRepository = costRecordRepository;
        this.modelPricing = modelPricing;
        this.userIdResolver = userIdResolver;
    }

    /**
     * 异步保存成本记录
     * <p>
     * 输入 token 分缓存命中（cacheReadInputTokens）与未命中两档计费：
     * 命中部分按缓存命中单价，其余按未命中单价，命中价通常远低于未命中价，
     * 拆分计费可避免多轮对话/Agent 编排场景下成本被严重高估。
     *
     * @param userId               用户ID（多 Agent 子任务场景为会话ID）
     * @param modelName            模型名称
     * @param cacheReadInputTokens 缓存命中的输入 token 数（可为 null，按 0 处理）
     * @param inputTokens          输入 token 总数（含缓存命中）
     * @param outputTokens         输出 token 数
     * @param operationType        场景标识（agent/skill、multi/planner 等）
     * @param conversationId       会话ID
     * @param promptContent        LLM 输入内容（prompt 全文，可为 null）
     * @param completionContent    LLM 输出内容（completion 全文，可为 null）
     */
    @Async
    public void saveCostRecord(String userId, String modelName, Long cacheReadInputTokens,
                                long inputTokens, long outputTokens,
                                String operationType, String conversationId,
                                String promptContent, String completionContent) {
        try {
            ModelPricing.Pricing p = modelPricing.get(modelName);

            long cacheRead = cacheReadInputTokens != null ? Math.min(cacheReadInputTokens, inputTokens) : 0;
            long cacheMiss = inputTokens - cacheRead;
            long totalTokens = inputTokens + outputTokens;
            BigDecimal inputCost = p.cacheReadInputPricePer1k()
                    .multiply(BigDecimal.valueOf(cacheRead))
                    .add(p.inputPricePer1k().multiply(BigDecimal.valueOf(cacheMiss)))
                    .divide(BigDecimal.valueOf(1000));
            BigDecimal outputCost = p.outputPricePer1k()
                    .multiply(BigDecimal.valueOf(outputTokens))
                    .divide(BigDecimal.valueOf(1000));
            BigDecimal totalCostCny = inputCost.add(outputCost);

            CostRecord record = new CostRecord(
                    null,
                    userId,
                    modelName,
                    cacheRead,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    totalCostCny,
                    operationType,
                    conversationId,
                    promptContent,
                    completionContent,
                    LocalDateTime.now());

            costRecordRepository.save(record);
            LOGGER.info("Cost record saved: userId={}, model={}, input={}(cacheRead={}), output={}, total={}, cost=¥{}",
                    record.getUserId(), record.getModelName(),
                    inputTokens, cacheRead, outputTokens, totalTokens, totalCostCny);
        } catch (Exception e) {
            LOGGER.error("Cost record save failed: userId={}, model={}, operation={}, conversationId={}",
                    userId, modelName, operationType, conversationId, e);
        }
    }

    /**
     * 便捷方法：从 ChatClient 请求/响应提取输入输出内容与 usage 异步保存成本记录（null-safe）。
     * 由 {@link TokenUsageCostAdvisor} 调用，prompt 取 {@link ChatClientRequest#prompt()} 的完整内容，
     * completion 取 {@link ChatResponse#getResults()} 全部 generation 的文本与工具调用，调用点无需重复解析。
     *
     * @param conversationId 会话ID
     * @param operationType  场景标识（格式：agent/multi 前缀 + 具体 handler/阶段，
     *                       如 agent/skill、agent/chat、multi/planner、multi/aggregator）
     * @param request        LLM 调用请求（提取输入内容，可为 null）
     * @param response       LLM 调用响应（提取 usage 与输出内容，可为 null）
     */
    @Async
    public void saveCostRecord(String conversationId, String operationType,
                               ChatClientRequest request, ChatClientResponse response) {
        if (response == null || response.chatResponse() == null) {
            return;
        }
        ChatResponse chatResponse = response.chatResponse();
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        Usage usage = metadata != null ? metadata.getUsage() : null;
        if (usage == null || usage.getPromptTokens() == null) {
            return;
        }
        String modelName = metadata.getModel() != null ? metadata.getModel() : "deepseek-v4-flash";
        // userId 通过 UserIdResolver 从会话ID解析，保证成本记录的用户维度正确（而非直接落会话ID）
        String userId = userIdResolver.resolve(conversationId);
        saveCostRecord(userId, modelName, usage.getCacheReadInputTokens(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(), operationType, conversationId,
                extractPrompt(request), extractCompletion(chatResponse));
    }

    /**
     * 提取请求的完整输入文本：prompt 内容 + entity 结构化输出的格式提示 + 工具定义。
     * entity() 的 JSON 格式提示存放在 context 的 OUTPUT_FORMAT 中，由最内层 ChatModelCallAdvisor
     * 在进入模型前才注入 prompt；工具定义在 prompt options（ToolCallingChatOptions）中由模型层注入，
     * 两者均需在此手动补上，保证留档内容与真实请求一致。
     */
    private String extractPrompt(ChatClientRequest request) {
        if (request == null || request.prompt() == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(request.prompt().getContents() != null
                ? request.prompt().getContents() : "");
        Object format = request.context().get(ChatClientAttributes.OUTPUT_FORMAT.getKey());
        if (format instanceof String f && !f.isBlank()) {
            sb.append("\n\n【entity 格式提示】\n").append(f);
        }
        List<ToolCallback> toolCallbacks = resolveToolCallbacks(request.prompt());
        if (!toolCallbacks.isEmpty()) {
            sb.append("\n\n【工具定义】\n");
            for (ToolCallback tc : toolCallbacks) {
                ToolDefinition def = tc.getToolDefinition();
                sb.append("- ").append(def.name()).append(": ").append(def.description()).append('\n');
                String schema = def.inputSchema();
                if (schema != null && !schema.isBlank()) {
                    sb.append("  schema: ").append(schema).append('\n');
                }
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 从 prompt options 解析工具回调列表；options 非 ToolCallingChatOptions 时返回空列表
     */
    private List<ToolCallback> resolveToolCallbacks(Prompt prompt) {
        if (prompt.getOptions() instanceof ToolCallingChatOptions options
                && options.getToolCallbacks() != null) {
            return options.getToolCallbacks();
        }
        return List.of();
    }

    /**
     * 从 ChatResponse 提取完整的 assistant 输出：拼接全部 generation 的文本与工具调用（JSON 参数），
     * 无任何内容时返回 null。
     */
    private String extractCompletion(ChatResponse chatResponse) {
        if (chatResponse.getResults() == null || chatResponse.getResults().isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (Generation generation : chatResponse.getResults()) {
            AssistantMessage output = generation.getOutput();
            if (output == null) {
                continue;
            }
            String text = output.getText();
            if (text != null && !text.isBlank()) {
                sb.append(text).append('\n');
            }
            List<AssistantMessage.ToolCall> toolCalls = output.getToolCalls();
            if (toolCalls != null) {
                for (AssistantMessage.ToolCall tc : toolCalls) {
                    sb.append("[工具调用] ").append(tc.name())
                            .append('(').append(tc.arguments()).append(")\n");
                }
            }
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? null : result;
    }

    /**
     * 查询用户成本统计
     */
    public List<CostRecord> getUserCostRecords(String userId, LocalDateTime start, LocalDateTime end) {
        return costRecordRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(userId, start, end);
    }

    /**
     * 查询模型成本统计
     */
    public List<CostRecord> getModelCostRecords(String modelName, LocalDateTime start, LocalDateTime end) {
        return costRecordRepository.findByModelNameAndCreatedAtBetweenOrderByCreatedAtDesc(modelName, start, end);
    }
}
