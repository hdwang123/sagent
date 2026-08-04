package com.example.sagent.agent.core;

import com.example.sagent.agent.model.AgentResponse;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.RouteDecision;
import com.example.sagent.agent.routing.MessageClassifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Agent服务类
 * 负责消息分类和路由到相应的处理器
 */
@Service
public class AgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AgentService.class);

    private final MessageClassifier classifier;
    private final HandlerRegistry handlerRegistry;

    /**
     * 构造函数
     *
     * @param classifier      消息分类器
     * @param handlerRegistry 处理器注册表
     */
    public AgentService(MessageClassifier classifier, HandlerRegistry handlerRegistry) {
        this.classifier = classifier;
        this.handlerRegistry = handlerRegistry;
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
        long start = System.nanoTime();
        RouteDecision decision = classifier.classify(conversationId, message);
        long classifyMs = (System.nanoTime() - start) / 1_000_000;

        AgentHandler handler = handlerRegistry.getOrDefault(decision.type(), handlerRegistry.get(AgentType.CHAT));
        if (handler == null) {
            // 极端情况：分类器返回了未注册类型且CHAT也未注册，直接返回错误而非抛500
            LOGGER.error("未注册任何处理器，type={}", decision.type());
            return new AgentResponse(
                    conversationId,
                    "系统暂无可用的处理器，请稍后再试",
                    decision.type(),
                    decision.reason(),
                    java.util.List.of(),
                    HandlerResult.CODE_ERROR
            );
        }
        if (handler.type() != decision.type()) {
            LOGGER.warn("未注册处理器[{}]，降级为普通聊天", decision.type());
        }
        start = System.nanoTime();
        HandlerResult result = handler.handle(conversationId, message);
        long handleMs = (System.nanoTime() - start) / 1_000_000;

        LOGGER.info("单Agent路由耗时: classify={}ms, handle[{}]={}ms, total={}ms",
                classifyMs, decision.type(), handleMs, classifyMs + handleMs);

        return new AgentResponse(
                conversationId,
                result.answer(),
                decision.type(),
                decision.reason(),
                result.sources(),
                result.code()
        );
    }

}
