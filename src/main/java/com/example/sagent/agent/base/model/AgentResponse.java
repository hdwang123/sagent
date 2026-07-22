package com.example.sagent.agent.base.model;

import java.util.List;

public record AgentResponse(
        String conversationId,
        String answer,
        AgentType type,
        String routeReason,
        List<String> sources
) {
    public AgentResponse {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
