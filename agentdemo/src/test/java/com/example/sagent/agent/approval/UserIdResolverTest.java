package com.example.sagent.agent.approval;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UserIdResolver 单元测试
 * <p>
 * 覆盖 null 输入、普通 conversationId、复合会话ID（conversationId#taskId）的解析逻辑，
 * 重点验证多 Agent 编排场景下同一用户的不同子任务会话ID能解析到一致的 userId。
 */
class UserIdResolverTest {

    private final UserIdResolver resolver = new UserIdResolver();

    @Test
    void resolve_null_returnsAnonymous() {
        assertThat(resolver.resolve(null)).isEqualTo("anonymous");
    }

    @Test
    void resolve_normalId_returnsConsistentUserId() {
        String userId1 = resolver.resolve("conv-123");
        String userId2 = resolver.resolve("conv-123");
        assertThat(userId1).isEqualTo(userId2);
        assertThat(userId1).startsWith("user-");
    }

    @Test
    void resolve_differentIds_returnDifferentUserIds() {
        String userId1 = resolver.resolve("conv-aaa");
        String userId2 = resolver.resolve("conv-bbb");
        assertThat(userId1).isNotEqualTo(userId2);
    }

    @Test
    void resolve_compositeId_extractsBaseId() {
        // 多 Agent 编排：同一会话的不同子任务使用复合会话ID
        String baseUserId = resolver.resolve("conv-123");
        String t1UserId = resolver.resolve("conv-123#t1");
        String t2UserId = resolver.resolve("conv-123#t2");

        assertThat(t1UserId).isEqualTo(baseUserId);
        assertThat(t2UserId).isEqualTo(baseUserId);
    }

    @Test
    void resolve_compositeId_differentBases_returnDifferentUserIds() {
        String userA = resolver.resolve("conv-a#t1");
        String userB = resolver.resolve("conv-b#t1");
        assertThat(userA).isNotEqualTo(userB);
    }

    @Test
    void resolve_multipleHashes_extractsBeforeFirstHash() {
        // 边界：conversationId 本身包含多个 #，取第一个 # 前
        String baseUserId = resolver.resolve("conv-123");
        String compositeUserId = resolver.resolve("conv-123#t1#extra");
        assertThat(compositeUserId).isEqualTo(baseUserId);
    }

    @Test
    void resolve_hashOnly_treatsAsEmptyBase() {
        // 边界：以 # 开头，提取后 baseId 为空字符串
        String userId = resolver.resolve("#t1");
        // 空字符串 hashCode 为 0，Math.abs(0 % 10000) = 0
        assertThat(userId).isEqualTo("user-0");
    }
}
