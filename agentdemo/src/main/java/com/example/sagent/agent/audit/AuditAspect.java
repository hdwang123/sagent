package com.example.sagent.agent.audit;

import com.example.sagent.agent.approval.UserIdResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

/**
 * 审计日志切面
 * 拦截带有 @AuditLog 注解的方法，记录操作日志
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserIdResolver userIdResolver;

    @Around("@annotation(auditLog)")
    public Object logAudit(ProceedingJoinPoint pjp, AuditLog auditLog) throws Throwable {
        long start = System.currentTimeMillis();

        // 从会话上下文解析 userId（无会话时使用兜底标识）
        String userId = userIdResolver.resolve("unknown");

        AuditLogEntity entity = AuditLogEntity.builder()
                .userId(userId)
                .operationType(auditLog.operationType().name())
                .resourceType(auditLog.resourceType().name())
                .resourceId(auditLog.resourceId())
                .operationDetail(auditLog.operationDetail())
                .status("STARTED")
                .createdAt(Instant.now())
                .build();

        try {
            Object result = pjp.proceed();
            entity.setStatus("SUCCESS");
            entity.setDurationMs(System.currentTimeMillis() - start);
            return result;
        } catch (Exception e) {
            entity.setStatus("FAILED");
            entity.setErrorMessage(e.getMessage());
            entity.setDurationMs(System.currentTimeMillis() - start);
            throw e;
        } finally {
            // 异步写入 DB，避免阻塞主流程
            CompletableFuture.runAsync(() -> {
                try {
                    auditLogRepository.save(entity);
                } catch (Exception ex) {
                    log.error("Failed to save audit log", ex);
                }
            });
        }
    }
}
