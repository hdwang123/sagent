package com.example.sagent.agent.core;

import com.example.sagent.agent.model.AgentResponse;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.RouteDecision;
import com.example.sagent.agent.routing.MessageClassifier;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Agent服务类
 * 负责消息分类和路由到相应的处理器
 */
@Service
public class AgentService {

    private final MessageClassifier classifier;
    private final Map<AgentType, AgentHandler> handlers;

    /**
     * 构造函数
     *
     * @param classifier 消息分类器
     * @param handlers   处理器列表
     */
    public AgentService(MessageClassifier classifier, List<AgentHandler> handlers) {
        this.classifier = classifier;
        this.handlers = new EnumMap<>(AgentType.class);
        for (AgentHandler handler : handlers) {
            this.handlers.put(handler.type(), handler);
        }
    }

    /**
     * 处理用户消息
     * 先分类消息，然后路由到相应的处理器处理
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return AgentResponse响应结果
     */
    public AgentResponse ask(String conversationId, String message) {
        RouteDecision decision = classifier.classify(conversationId, message);
        AgentHandler handler = handlers.get(decision.type());
        if (handler == null) {
            throw new IllegalStateException("No handler registered for " + decision.type());
        }
        HandlerResult result = handler.handle(conversationId, message);
        return new AgentResponse(
                conversationId,
                result.answer(),
                decision.type(),
                decision.reason(),
                result.sources()
        );
    }

}