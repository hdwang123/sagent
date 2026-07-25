package com.example.sagent.agent.skills;

/**
 * 审批上下文
 * 通过 ThreadLocal 在 ASkillHandler 和 ApprovalSqlSkill 之间传递当前会话 ID
 */
public final class ApprovalContext {

    private static final ThreadLocal<String> CONVERSATION_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> USER_ID = new ThreadLocal<>();

    private ApprovalContext() {
    }

    public static void setConversationId(String conversationId) {
        CONVERSATION_ID.set(conversationId);
    }

    public static String getConversationId() {
        return CONVERSATION_ID.get();
    }

    public static void setUserId(String userId) {
        USER_ID.set(userId);
    }

    public static String getUserId() {
        return USER_ID.get();
    }

    public static void clear() {
        CONVERSATION_ID.remove();
        USER_ID.remove();
    }
}
