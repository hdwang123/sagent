package com.example.sagent.controller;

import com.example.sagent.agent.audit.AuditLogEntity;
import com.example.sagent.agent.audit.AuditLogRepository;
import com.example.sagent.agent.cost.CostRecord;
import com.example.sagent.agent.cost.CostRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 审计和成本查询接口
 */
@Slf4j
@RestController
@RequestMapping("/api/admin/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditLogRepository auditLogRepository;
    private final CostRecordRepository costRecordRepository;

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
        BigDecimal totalCost = records.stream()
                .map(CostRecord::getCostUsd)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Map.of(
                "totalInputTokens", totalInput,
                "totalOutputTokens", totalOutput,
                "totalTokens", totalInput + totalOutput,
                "totalCost", totalCost,
                "records", records
        );
    }
}
