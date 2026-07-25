package com.example.sagent.agent.approval;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 审批注解
 * 标注在 ASkill 的 @Tool 方法上，标记该操作是否需要人工审批
 *
 *   enable=true  (默认) - 该操作需要审批，调用时先创建审批记录
 *   enable=false        - 该操作无需审批，直接执行
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Approval {
    boolean enable() default true;
}
