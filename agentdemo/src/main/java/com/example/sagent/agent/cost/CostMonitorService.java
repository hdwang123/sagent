package com.example.sagent.agent.cost;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    public CostMonitorService(CostRecordRepository costRecordRepository) {
        this.costRecordRepository = costRecordRepository;
    }

    /**
     * 异步保存成本记录
     */
    @Async
    public void saveCostRecord(String userId, String modelName,
                                long inputTokens, long outputTokens,
                                String operationType, String conversationId) {
        try {
            ModelPricing.Pricing p = ModelPricing.get(modelName);

            long totalTokens = inputTokens + outputTokens;
            BigDecimal inputCost = p.inputPricePer1k()
                    .multiply(BigDecimal.valueOf(inputTokens))
                    .divide(BigDecimal.valueOf(1000));
            BigDecimal outputCost = p.outputPricePer1k()
                    .multiply(BigDecimal.valueOf(outputTokens))
                    .divide(BigDecimal.valueOf(1000));
            BigDecimal totalCostCny = inputCost.add(outputCost);

            CostRecord record = new CostRecord(
                    null,
                    userId,
                    modelName,
                    inputTokens,
                    outputTokens,
                    totalTokens,
                    totalCostCny,
                    operationType,
                    conversationId,
                    LocalDateTime.now());

            costRecordRepository.save(record);
            LOGGER.info("Cost record saved: userId={}, model={}, input={}, output={}, total={}, cost=¥{}",
                    record.getUserId(), record.getModelName(),
                    inputTokens, outputTokens, totalTokens, totalCostCny);
        } catch (Exception e) {
            LOGGER.error("Cost record save failed: userId={}, model={}, operation={}, conversationId={}",
                    userId, modelName, operationType, conversationId, e);
        }
    }

    /**
     * 便捷方法：从 ChatResponse 提取 usage 异步保存成本记录（null-safe）
     * 调用点只需传会话ID、操作类型与响应对象，避免各 Handler 重复解析 usage
     *
     * @param conversationId 会话ID
     * @param operationType  场景标识（格式：agent/multi 前缀 + 具体 handler/阶段，
     *                       如 agent/skill、agent/chat、multi/planner、multi/aggregator）
     * @param chatResponse   LLM 调用响应（可能为 null）
     */
    @Async
    public void saveCostRecord(String conversationId, String operationType, ChatResponse chatResponse) {
        if (chatResponse == null) {
            return;
        }
        ChatResponseMetadata metadata = chatResponse.getMetadata();
        Usage usage = metadata != null ? metadata.getUsage() : null;
        if (usage == null || usage.getPromptTokens() == null) {
            return;
        }
        String modelName = metadata.getModel() != null ? metadata.getModel() : "deepseek-chat";
        saveCostRecord(conversationId, modelName, usage.getPromptTokens(),
                usage.getCompletionTokens(), operationType, conversationId);
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
