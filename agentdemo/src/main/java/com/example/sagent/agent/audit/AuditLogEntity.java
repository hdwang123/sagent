package com.example.sagent.agent.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * 审计日志实体
 * <p>
 * 注：JPA 实体要求无参构造 + 可变类（代理/水合机制），无法使用 Java record，
 * 故采用全参构造 + getter 的不可变风格替代 Lombok。
 */
@Entity
@Table(name = "audit_log")
public class AuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String userId;

    @Column(nullable = false, length = 32)
    private String operationType;

    @Column(nullable = false, length = 32)
    private String resourceType;

    @Column(length = 128)
    private String resourceId;

    @Column(columnDefinition = "TEXT")
    private String operationDetail;

    @Column(nullable = false, length = 16)
    private String status;  // STARTED/SUCCESS/FAILED

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column
    private Long durationMs;

    @Column(nullable = false)
    private Instant createdAt;

    /** JPA 规范要求提供无参构造 */
    protected AuditLogEntity() {
    }

    public AuditLogEntity(Long id, String userId, String operationType, String resourceType,
                          String resourceId, String operationDetail, String status,
                          String errorMessage, Long durationMs, Instant createdAt) {
        this.id = id;
        this.userId = userId;
        this.operationType = operationType;
        this.resourceType = resourceType;
        this.resourceId = resourceId;
        this.operationDetail = operationDetail;
        this.status = status;
        this.errorMessage = errorMessage;
        this.durationMs = durationMs;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getOperationType() {
        return operationType;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }

    public String getOperationDetail() {
        return operationDetail;
    }

    public String getStatus() {
        return status;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
