package com.example.sagent.agent.cost;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成本记录实体
 * 记录 LLM 调用的 token 消耗和费用
 */
@Entity
@Table(name = "cost_record")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CostRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String modelName;

    @Column(nullable = false)
    private Long inputTokens;

    @Column(nullable = false)
    private Long outputTokens;

    @Column(nullable = false)
    private Long totalTokens;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal costUsd;

    @Column(nullable = false, length = 32)
    private String operationType;

    @Column(length = 128)
    private String conversationId;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
