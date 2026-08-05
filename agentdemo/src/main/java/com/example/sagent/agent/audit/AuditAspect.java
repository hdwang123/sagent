package com.example.sagent.agent.audit;

import com.example.sagent.agent.approval.UserIdResolver;
import com.example.sagent.agent.skills.ApprovalContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * 审计日志切面
 * 拦截带有 @AuditLog 注解的方法，记录操作日志
 */
@Aspect
@Component
public class AuditAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(AuditAspect.class);

    private final AuditLogRepository auditLogRepository;
    private final UserIdResolver userIdResolver;

    public AuditAspect(AuditLogRepository auditLogRepository, UserIdResolver userIdResolver) {
        this.auditLogRepository = auditLogRepository;
        this.userIdResolver = userIdResolver;
    }

    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();

        // 从 ThreadLocal 会话上下文取 userId（会话入口已绑定）
        String userId = resolveUserId();

        Throwable error = null;
        try {
            return pjp.proceed();
        } catch (Throwable t) {
            error = t;
            throw t;
        } finally {
            long durationMs = System.currentTimeMillis() - start;
            String status = error == null ? "SUCCESS" : "FAILED";
            AuditLogEntity entity = new AuditLogEntity(
                    null,
                    userId,
                    auditLog.operationType().name(),
                    auditLog.resourceType().name(),
                    auditLog.resourceId(),
                    auditLog.operationDetail(),
                    status,
                    error == null ? null : error.getMessage(),
                    durationMs,
                    Instant.now());
            // 异步写入 DB，避免阻塞主流程
            CompletableFuture.runAsync(() -> {
                try {
                    auditLogRepository.save(entity);
                } catch (Exception ex) {
                    LOGGER.error("Failed to save audit log", ex);
                }
            });
        }
    }

    /**
     * 从 {@link ApprovalContext}（ThreadLocal）解析 userId：
     * 优先取绑定的 userId，其次用绑定的 conversationId 映射，均取不到时兜底 unknown
     */
    private String resolveUserId() {
        String userId = ApprovalContext.getUserId();
        if (userId != null && !userId.isBlank()) {
            return userId;
        }
        String conversationId = ApprovalContext.getConversationId();
        if (conversationId != null && !conversationId.isBlank()) {
            return userIdResolver.resolve(conversationId);
        }
        return "unknown";
    }
}
