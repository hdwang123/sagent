package com.example.sagent.agent.model;

import java.util.List;

/**
 * Agent响应对象
 * 封装Agent处理后的响应结果
 */
public record AgentResponse(
        /**
         * 会话ID
         */
        String conversationId,
        /**
         * 回答内容
         */
        String answer,
        /**
         * 处理类型
         */
        AgentType type,
        /**
         * 路由原因
         */
        String routeReason,
        /**
         * 来源列表（如知识库文档名）
         */
        List<String> sources
) {

    /**
     * 构造函数，确保sources不为null
     */
    public AgentResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}