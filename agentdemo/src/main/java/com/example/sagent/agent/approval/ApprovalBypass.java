package com.example.sagent.agent.approval;

/**
 * 审批绕过标志
 * 审批面板直接执行工具时设上，切面检测到后退过审批逻辑
 */
public final class ApprovalBypass {

    private static final ThreadLocal<Boolean> BYPASS = ThreadLocal.withInitial(() -> false);

    private ApprovalBypass() {}

    public static void enable() { BYPASS.set(true); }
    public static void disable() { BYPASS.set(false); }
    public static boolean isActive() { return BYPASS.get(); }
}
