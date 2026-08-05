package com.example.sagent.agent.audit;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 审计日志实体
 */
@Entity
@Table(name = "audit_log")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
}
