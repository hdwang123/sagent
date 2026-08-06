package com.example.sagent.controller;

import com.example.sagent.agent.audit.AuditLogEntity;
import com.example.sagent.agent.audit.AuditLogRepository;
import com.example.sagent.agent.cost.CostRecord;
import com.example.sagent.agent.cost.CostRecordRepository;
import com.example.sagent.agent.cost.ModelPricing;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审计和成本查询接口
 */
@RestController
@RequestMapping("/api/admin/audit")
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final CostRecordRepository costRecordRepository;
    private final ModelPricing modelPricing;

    public AuditController(AuditLogRepository auditLogRepository,
                           CostRecordRepository costRecordRepository,
                           ModelPricing modelPricing) {
        this.auditLogRepository = auditLogRepository;
        this.costRecordRepository = costRecordRepository;
        this.modelPricing = modelPricing;
    }

    /**
     * 查询审计日志
     */
    @GetMapping("/list")
    public Page<AuditLogEntity> list(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @PageableDefault(size = 50, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        if (userId != null && startDate != null && endDate != null) {
            return auditLogRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    userId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59), pageable);
        }
        if (userId != null) {
            return auditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }
        return auditLogRepository.findAll(pageable);
    }

    /**
     * 查询成本统计（按用户 + 时间范围汇总 token 与费用）
     */
    @GetMapping("/cost")
    public Map<String, Object> costRecords(
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        List<CostRecord> records;
        if (userId != null && startDate != null && endDate != null) {
            records = costRecordRepository.findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(
                    userId, startDate.atStartOfDay(), endDate.atTime(23, 59, 59));
        } else if (userId != null) {
            records = costRecordRepository.findByUserIdOrderByCreatedAtDesc(userId);
        } else {
            records = costRecordRepository.findAll(Sort.by(Sort.Direction.DESC, "createdAt"));
        }

        long totalInput = records.stream().mapToLong(CostRecord::getInputTokens).sum();
        long totalOutput = records.stream().mapToLong(CostRecord::getOutputTokens).sum();
        BigDecimal totalCostCny = records.stream()
                .map(CostRecord::getCostCny)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> rows = records.stream()
                .map(this::toRowWithFormula)
                .toList();

        return Map.of(
                "totalInputTokens", totalInput,
                "totalOutputTokens", totalOutput,
                "totalTokens", totalInput + totalOutput,
                "totalCostCny", totalCostCny,
                "records", rows
        );
    }

    /**
     * 将成本记录转换为展示行（附带计费公式），供前端直接渲染
     *
     * @param r 成本记录
     * @return 包含记录全部字段与 formula 的 Map
     */
    private Map<String, Object> toRowWithFormula(CostRecord r) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", r.getId());
        row.put("userId", r.getUserId());
        row.put("modelName", r.getModelName());
        row.put("cacheReadInputTokens", r.getCacheReadInputTokens());
        row.put("inputTokens", r.getInputTokens());
        row.put("outputTokens", r.getOutputTokens());
        row.put("totalTokens", r.getTotalTokens());
        row.put("costCny", r.getCostCny());
        row.put("operationType", r.getOperationType());
        row.put("conversationId", r.getConversationId());
        row.put("promptContent", r.getPromptContent());
        row.put("completionContent", r.getCompletionContent());
        row.put("createdAt", r.getCreatedAt());
        row.put("formula", buildFormula(r));
        return row;
    }

    /**
     * 构建计费公式字符串：逐项列出发票单价与 token 数，省略为 0 的项。
     * 单价单位为 ¥/1K tokens，故整体需除以 1000。
     * 例：(400×0.00002 + 600×0.001 + 500×0.002) / 1000 = ¥0.001608
     *
     * @param r 成本记录
     * @return 计费公式
     */
    private String buildFormula(CostRecord r) {
        ModelPricing.Pricing p = modelPricing.get(r.getModelName());
        long cacheRead = r.getCacheReadInputTokens() != null ? r.getCacheReadInputTokens() : 0;
        long miss = Math.max(0, r.getInputTokens() - cacheRead);
        List<String> terms = new ArrayList<>();
        if (cacheRead > 0) {
            terms.add(cacheRead + "×" + p.cacheReadInputPricePer1k());
        }
        if (miss > 0) {
            terms.add(miss + "×" + p.inputPricePer1k());
        }
        if (r.getOutputTokens() > 0) {
            terms.add(r.getOutputTokens() + "×" + p.outputPricePer1k());
        }
        if (terms.isEmpty()) {
            return "0 = ¥" + r.getCostCny();
        }
        return "(" + String.join(" + ", terms) + ") / 1000 = ¥" + r.getCostCny();
    }
}
