package com.example.sagent.agent.core;

import com.example.sagent.agent.model.AgentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * HandlerRegistry 单元测试
 * <p>
 * 覆盖按类型注册、查询、降级查询等路由核心逻辑。
 */
class HandlerRegistryTest {

    @Test
    void get_registeredType_returnsHandler() {
        AgentHandler chatHandler = mockHandler(AgentType.CHAT);
        AgentHandler ragHandler = mockHandler(AgentType.RAG);
        HandlerRegistry registry = new HandlerRegistry(List.of(chatHandler, ragHandler));

        assertThat(registry.get(AgentType.CHAT)).isSameAs(chatHandler);
        assertThat(registry.get(AgentType.RAG)).isSameAs(ragHandler);
    }

    @Test
    void get_unregisteredType_returnsNull() {
        AgentHandler chatHandler = mockHandler(AgentType.CHAT);
        HandlerRegistry registry = new HandlerRegistry(List.of(chatHandler));

        assertThat(registry.get(AgentType.SKILL)).isNull();
    }

    @Test
    void getOrDefault_registeredType_returnsHandler() {
        AgentHandler chatHandler = mockHandler(AgentType.CHAT);
        AgentHandler fallback = mockHandler(AgentType.CHAT);
        HandlerRegistry registry = new HandlerRegistry(List.of(chatHandler));

        assertThat(registry.getOrDefault(AgentType.CHAT, fallback)).isSameAs(chatHandler);
    }

    @Test
    void getOrDefault_unregisteredType_returnsFallback() {
        AgentHandler chatHandler = mockHandler(AgentType.CHAT);
        AgentHandler fallback = mockHandler(AgentType.SKILL);
        HandlerRegistry registry = new HandlerRegistry(List.of(chatHandler));

        assertThat(registry.getOrDefault(AgentType.SKILL, fallback)).isSameAs(fallback);
    }

    @Test
    void duplicateType_lastOneWins() {
        // 同类型多个 Bean：EnumMap put 覆盖，后注册的覆盖先注册的
        AgentHandler first = mockHandler(AgentType.CHAT);
        AgentHandler second = mockHandler(AgentType.CHAT);
        HandlerRegistry registry = new HandlerRegistry(List.of(first, second));

        assertThat(registry.get(AgentType.CHAT)).isSameAs(second);
    }

    @Test
    void emptyHandlerList_allTypesReturnNull() {
        HandlerRegistry registry = new HandlerRegistry(List.of());
        for (AgentType type : AgentType.values()) {
            assertThat(registry.get(type)).isNull();
        }
    }

    private AgentHandler mockHandler(AgentType type) {
        AgentHandler handler = mock(AgentHandler.class);
        when(handler.type()).thenReturn(type);
        return handler;
    }
}
