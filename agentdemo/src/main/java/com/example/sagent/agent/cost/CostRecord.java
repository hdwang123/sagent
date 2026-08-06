package com.example.sagent.agent.cost;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 成本记录实体
 * 记录 LLM 调用的 token 消耗和费用
 * <p>
 * 注：JPA 实体要求无参构造 + 可变类（代理/水合机制），无法使用 Java record，
 * 故采用全参构造 + getter 的不可变风格替代 Lombok。
 */
@Entity
@Table(name = "cost_record")
public class CostRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 64)
    private String modelName;

    /** 缓存命中的输入 token 数（null 表示无命中信息） */
    @Column(name = "cache_read_input_tokens")
    private Long cacheReadInputTokens;

    @Column(nullable = false)
    private Long inputTokens;

    @Column(nullable = false)
    private Long outputTokens;

    @Column(nullable = false)
    private Long totalTokens;

    @Column(nullable = false, precision = 10, scale = 6)
    private BigDecimal costCny;

    @Column(nullable = false, length = 32)
    private String operationType;

    @Column(length = 128)
    private String conversationId;

    /** LLM 输入内容（prompt 全文，含系统提示、历史与上下文） */
    @Lob
    @Column(name = "prompt_content")
    private String promptContent;

    /** LLM 输出内容（completion 全文） */
    @Lob
    @Column(name = "completion_content")
    private String completionContent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** JPA 规范要求提供无参构造 */
    protected CostRecord() {
    }

    public CostRecord(Long id, String userId, String modelName,
                      Long cacheReadInputTokens, Long inputTokens, Long outputTokens, Long totalTokens,
                      BigDecimal costCny, String operationType,
                      String conversationId, String promptContent, String completionContent,
                      LocalDateTime createdAt) {
        this.id = id;
        this.userId = userId;
        this.modelName = modelName;
        this.cacheReadInputTokens = cacheReadInputTokens;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.totalTokens = totalTokens;
        this.costCny = costCny;
        this.operationType = operationType;
        this.conversationId = conversationId;
        this.promptContent = promptContent;
        this.completionContent = completionContent;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getModelName() {
        return modelName;
    }

    public Long getCacheReadInputTokens() {
        return cacheReadInputTokens;
    }

    public Long getInputTokens() {
        return inputTokens;
    }

    public Long getOutputTokens() {
        return outputTokens;
    }

    public Long getTotalTokens() {
        return totalTokens;
    }

    public BigDecimal getCostCny() {
        return costCny;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getConversationId() {
        return conversationId;
    }

    public String getPromptContent() {
        return promptContent;
    }

    public String getCompletionContent() {
        return completionContent;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
