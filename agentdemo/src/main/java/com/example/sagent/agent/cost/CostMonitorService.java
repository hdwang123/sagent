package com.example.sagent.agent.cost;

import com.example.sagent.agent.cost.CostRecord;
import com.example.sagent.agent.cost.CostRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 成本监控服务
 * 异步记录 LLM 调用的 token 消耗和费用
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CostMonitorService {

    private final CostRecordRepository costRecordRepository;
    private final ModelPricing pricing;

    /**
     * 异步保存成本记录
     */
    @Async
    public void saveCostRecord(String userId, String modelName,
                                long inputTokens, long outputTokens,
                                String operationType, String conversationId) {
        ModelPricing.Pricing p = pricing.get(modelName);
        if (p == null) {
            log.warn("No pricing found for model: {}", modelName);
            return;
        }

        long totalTokens = inputTokens + outputTokens;
        BigDecimal inputCost = p.inputPricePer1k()
                .multiply(BigDecimal.valueOf(inputTokens))
                .divide(BigDecimal.valueOf(1000));
        BigDecimal outputCost = p.outputPricePer1k()
                .multiply(BigDecimal.valueOf(outputTokens))
                .divide(BigDecimal.valueOf(1000));
        BigDecimal totalCost = inputCost.add(outputCost);

        CostRecord record = CostRecord.builder()
                .userId(userId)
                .modelName(modelName)
                .inputTokens(inputTokens)
                .outputTokens(outputTokens)
                .totalTokens(totalTokens)
                .costUsd(totalCost)
                .operationType(operationType)
                .conversationId(conversationId)
                .createdAt(LocalDateTime.now())
                .build();

        costRecordRepository.save(record);
        log.info("Cost record saved: userId={}, model={}, input={}, output={}, total={}, cost=${}",
                record.getUserId(), record.getModelName(),
                inputTokens, outputTokens, totalTokens, totalCost);
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
