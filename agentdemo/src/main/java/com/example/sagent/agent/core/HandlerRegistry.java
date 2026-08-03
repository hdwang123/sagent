package com.example.sagent.agent.core;

import com.example.sagent.agent.model.AgentType;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Agent处理器注册表
 * 统一将容器中的 AgentHandler Bean 按类型索引为 EnumMap，
 * 供 AgentService（单Agent路由）与 MultiAgentService（多Agent编排）共用
 */
@Component
public class HandlerRegistry {

    private final Map<AgentType, AgentHandler> handlers;

    public HandlerRegistry(List<AgentHandler> handlers) {
        this.handlers = new EnumMap<>(AgentType.class);
        for (AgentHandler handler : handlers) {
            this.handlers.put(handler.type(), handler);
        }
    }

    /**
     * 获取指定类型的处理器
     *
     * @param type Agent类型
     * @return 对应的处理器；未注册时返回null
     */
    public AgentHandler get(AgentType type) {
        return handlers.get(type);
    }

    /**
     * 获取指定类型的处理器，不存在时返回降级处理器
     *
     * @param type     Agent类型
     * @param fallback 降级处理器
     * @return 对应的处理器或降级处理器
     */
    public AgentHandler getOrDefault(AgentType type, AgentHandler fallback) {
        return handlers.getOrDefault(type, fallback);
    }
}
