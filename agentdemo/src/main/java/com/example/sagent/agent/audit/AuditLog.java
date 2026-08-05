package com.example.sagent.agent.audit;

import java.lang.annotation.*;

/**
 * 审计日志注解
 * 用于标记需要记录审计日志的方法
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditLog {
    OperationType operationType() default OperationType.TOOL_CALL;
    ResourceType resourceType() default ResourceType.TOOL;
    String resourceId() default "";
    String operationDetail() default "";
}
