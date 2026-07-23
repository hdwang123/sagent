package com.example.sagent.agent.model;

/**
 * 路由决策对象
 * 封装消息分类后的路由结果
 */
public record RouteDecision(
        /**
         * 目标处理类型
         */
        AgentType type,
        /**
         * 分类原因
         */
        String reason
) {
}