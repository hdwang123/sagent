package com.example.sagent.agent.core;

import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;

/**
 * Agent处理器接口
 * 定义所有Agent处理器必须实现的方法
 */
public interface AgentHandler {

    /**
     * 获取处理器类型
     *
     * @return AgentType枚举值
     */
    AgentType type();

    /**
     * 处理消息
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果
     */
    HandlerResult handle(String conversationId, String message);
}