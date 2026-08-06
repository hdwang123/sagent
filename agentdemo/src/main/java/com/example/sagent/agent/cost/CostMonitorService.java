package com.example.sagent.agent.cost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
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

    public CostMonitorService(CostRecordRepository costRecordRepository, ModelPricing modelPricing) {
        this.costRecordRepository = costRecordRepository;
        this.modelPricing = modelPricing;
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
     * completion 取 {@link ChatResponse#getResult()} 的 assistant 文本，调用点无需重复解析。
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
        String promptContent = request != null && request.prompt() != null ? request.prompt().getContents() : null;
        saveCostRecord(conversationId, modelName, usage.getCacheReadInputTokens(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(), operationType, conversationId,
                promptContent, extractCompletion(chatResponse));
    }

    /**
     * 从 ChatResponse 提取 assistant 输出文本；无结果或空文本时返回 null
     */
    private String extractCompletion(ChatResponse chatResponse) {
        if (chatResponse.getResult() == null || chatResponse.getResult().getOutput() == null) {
            return null;
        }
        String text = chatResponse.getResult().getOutput().getText();
        return (text == null || text.isBlank()) ? null : text;
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
