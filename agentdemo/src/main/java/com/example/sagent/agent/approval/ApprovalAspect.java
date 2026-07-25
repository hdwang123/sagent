package com.example.sagent.agent.approval;

import com.example.sagent.agent.model.ApprovalRecord;
import com.example.sagent.agent.skills.ApprovalContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;

@Aspect
@Component
public class ApprovalAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalAspect.class);

    private final ApprovalService approvalService;

    public ApprovalAspect(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Around("execution(* com.example.sagent.agent.skills.ASkill+.*(..)) && @annotation(approval)")
    public Object checkApproval(ProceedingJoinPoint pjp, Approval approval) throws Throwable {
        String methodName = pjp.getSignature().getName();
        LOGGER.info("ApprovalAspect intercepting {}", methodName);

        // 审批面板直接调用 -> 放行
        if (ApprovalBypass.isActive()) {
            LOGGER.info("ApprovalBypass active, proceeding {}", methodName);
            return pjp.proceed();
        }

        if (!approval.enable()) {
            return pjp.proceed();
        }

        Object[] args = pjp.getArgs();

        String userId = ApprovalContext.getUserId();
        if (userId == null) {
            userId = "anonymous";
            LOGGER.warn("ApprovalContext.userId is null, using fallback 'anonymous' for {}", methodName);
        }

        // 按方法参数名生成 JSON，与 ToolController.resolveArgs 解析格式一致
        MethodSignature sig = (MethodSignature) pjp.getSignature();
        String[] paramNames = java.util.Arrays.stream(sig.getMethod().getParameters())
                .map(Parameter::getName)
                .toArray(String[]::new);
        String argsJson = buildArgsJson(methodName, paramNames, args);

        // 每次 LLM 调用都强制创建审批记录，不执行原始方法
        ApprovalRecord record = approvalService.createPending(userId, methodName, argsJson);
        LOGGER.info("approval required for {} {}, created #{}", methodName, argsJson, record.id());
        return "PENDING:" + record.id();
    }

    private String buildArgsJson(String methodName, String[] paramNames, Object[] args) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        if (paramNames != null) {
            for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                if (paramNames[i].startsWith("arg")) continue;
                if (!first) sb.append(",");
                sb.append("\"").append(paramNames[i]).append("\":\"")
                        .append(args[i] != null ? args[i].toString() : "null").append("\"");
                if (first) first = false;
            }
        }
        sb.append("}");
        return sb.toString();
    }
}
