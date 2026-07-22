package com.example.sagent.agent.core;

import com.example.sagent.agent.base.model.AgentType;
import com.example.sagent.agent.base.model.HandlerResult;

public interface AgentHandler {

    AgentType type();

    HandlerResult handle(String conversationId, String message);
}