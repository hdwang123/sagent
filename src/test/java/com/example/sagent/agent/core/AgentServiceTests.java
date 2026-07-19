package com.example.sagent.agent.core;

import com.example.sagent.agent.model.AgentResponse;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.RouteDecision;
import com.example.sagent.agent.routing.MessageClassifier;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentServiceTests {

    @Test
    void routesMessageToClassifiedHandler() {
        MessageClassifier classifier = mock(MessageClassifier.class);
        AgentHandler chatHandler = handler(AgentType.CHAT);
        AgentHandler ragHandler = handler(AgentType.RAG);
        AgentHandler databaseHandler = handler(AgentType.DATABASE);
        AgentService agentService = new AgentService(
                classifier,
                List.of(chatHandler, ragHandler, databaseHandler)
        );

        when(classifier.classify("conversation-1", "如何配置 Sagent？"))
                .thenReturn(new RouteDecision(AgentType.RAG, "属于项目知识问题"));
        when(ragHandler.handle("conversation-1", "如何配置 Sagent？"))
                .thenReturn(new HandlerResult("从知识库得到的答案", List.of("sagent-overview.md")));

        AgentResponse response = agentService.ask("conversation-1", "如何配置 Sagent？");

        assertThat(response.conversationId()).isEqualTo("conversation-1");
        assertThat(response.type()).isEqualTo(AgentType.RAG);
        assertThat(response.answer()).isEqualTo("从知识库得到的答案");
        assertThat(response.routeReason()).isEqualTo("属于项目知识问题");
        assertThat(response.sources()).containsExactly("sagent-overview.md");
        verify(ragHandler).handle("conversation-1", "如何配置 Sagent？");
    }

    private AgentHandler handler(AgentType type) {
        AgentHandler handler = mock(AgentHandler.class);
        when(handler.type()).thenReturn(type);
        return handler;
    }
}
