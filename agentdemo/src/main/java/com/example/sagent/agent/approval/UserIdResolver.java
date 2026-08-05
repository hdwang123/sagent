package com.example.sagent.agent.approval;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户 ID 解析器
 * 将 conversationId 映射到 userId
 * 同一 conversationId 始终映射到同一 userId
 *
 * 实际项目中 userId 通常从 Spring Security 或 JWT 中获取
 */
@Component
public class UserIdResolver {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    /**
     * 根据 conversationId 解析 userId
     * 第一次遇到新的 conversationId 时，为该会话分配一个 userId
     */
    public String resolve(String conversationId) {
        if (conversationId == null) return "anonymous";
        // 兼容多Agent编排的复合会话ID（格式：原始conversationId#taskId），提取#前的原始ID
        String baseId = conversationId.contains("#")
                ? conversationId.substring(0, conversationId.indexOf('#'))
                : conversationId;
        return cache.computeIfAbsent(baseId, cid -> {
            // 为每个 conversationId 分配固定的 userId（demo 场景）
            // 实际项目中应替换为真实用户体系
            return "user-" + Math.abs(cid.hashCode() % 10000);
        });
    }
}
