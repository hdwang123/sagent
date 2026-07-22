package com.example.sagent.agent.core;

import com.example.sagent.agent.base.model.AgentResponse;
import com.example.sagent.agent.base.model.AgentType;
import com.example.sagent.agent.base.model.HandlerResult;
import com.example.sagent.agent.base.model.RouteDecision;
import com.example.sagent.agent.routing.MessageClassifier;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class AgentService {

    private final MessageClassifier classifier;
    private final Map<AgentType, AgentHandler> handlers;

    public AgentService(MessageClassifier classifier, List<AgentHandler> handlers) {
        this.classifier = classifier;
        this.handlers = new EnumMap<>(AgentType.class);
        for (AgentHandler handler : handlers) {
            this.handlers.put(handler.type(), handler);
        }
    }

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