package com.example.sagent.agent.approval;

import com.example.sagent.agent.model.ApprovalRecord;
import com.example.sagent.agent.skills.ApprovalContext;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.lang.reflect.Parameter;

/**
 * 审批切面
 * 拦截所有 ASkill 子类的 @Tool 方法调用：
 * 审批面板直接调用时放行（ApprovalBypass），LLM 调用时创建 PENDING 记录并返回 "PENDING:记录ID"
 */
@Aspect
@Component
public class ApprovalAspect {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalAspect.class);

    private final ApprovalService approvalService;
    private final UserIdResolver userIdResolver;
    private final ObjectMapper objectMapper;

    public ApprovalAspect(ApprovalService approvalService, UserIdResolver userIdResolver, ObjectMapper objectMapper) {
        this.approvalService = approvalService;
        this.userIdResolver = userIdResolver;
        this.objectMapper = objectMapper;
    }

    /**
     * 环绕通知：拦截 @Approval 标注的 @Tool 方法
     * ApprovalBypass 激活时直接放行（审批面板重新执行）；否则创建 PENDING 记录，不执行原方法
     *
     * @param pjp AOP 连接点
     * @param approval 方法上的 @Approval 注解
     * @return 原方法返回值，或 "PENDING:记录ID" 字符串
     * @throws Throwable 原方法抛出的异常
     */
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

        // 从会话入口绑定的 conversationId 推导 userId，不依赖 ThreadLocal 传递
        String conversationId = ApprovalContext.getConversationId();
        String userId = userIdResolver.resolve(conversationId);
        LOGGER.info("resolved userId={} from conversationId={}", userId, conversationId);

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
        ObjectNode node = objectMapper.createObjectNode();
        if (paramNames != null) {
            for (int i = 0; i < Math.min(paramNames.length, args.length); i++) {
                // 跳过编译期默认参数名（未开启 -parameters 时可能为 arg0/arg1）
                if (paramNames[i].startsWith("arg")) continue;
                node.put(paramNames[i], args[i] != null ? String.valueOf(args[i]) : "");
            }
        }
        return node.toString();
    }
}
