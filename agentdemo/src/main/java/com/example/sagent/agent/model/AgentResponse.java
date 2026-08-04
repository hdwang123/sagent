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
        List<String> sources,
        /**
         * 业务状态码，与 {@link HandlerResult#code()} 语义一致：
         * 200=成功，4xx=业务失败，5xx=技术错误。
         * 替代旧版 boolean error 字段，前端可据此展示错误样式。
         */
        int code
) {

    /**
     * 构造函数，确保sources不为null
     */
    public AgentResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
