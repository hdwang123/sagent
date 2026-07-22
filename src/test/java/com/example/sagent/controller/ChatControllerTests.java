package com.example.sagent.controller;

import com.example.sagent.agent.core.AgentService;
import com.example.sagent.agent.base.model.AgentResponse;
import com.example.sagent.agent.base.model.AgentType;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatControllerTests {

    @Test
    void generatesConversationIdWhenRequestDoesNotProvideOne() {
        AgentService agentService = mock(AgentService.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        ChatController controller = new ChatController(agentService, chatMemory);
        when(agentService.ask(anyString(), eq("你好"))).thenAnswer(invocation ->
                new AgentResponse(
                        invocation.getArgument(0),
                        "你好",
                        AgentType.CHAT,
                        "普通聊天",
                        List.of()
                )
        );

        AgentResponse response = controller.chat(
                new ChatController.ChatRequest(null, " 你好 ")
        );

        assertThat(response.conversationId()).isNotBlank();
        verify(agentService).ask(response.conversationId(), "你好");
    }

    @Test
    void clearsServerSideConversationMemory() {
        AgentService agentService = mock(AgentService.class);
        ChatMemory chatMemory = mock(ChatMemory.class);
        ChatController controller = new ChatController(agentService, chatMemory);

        controller.clearConversation(" conversation-1 ");

        verify(chatMemory).clear("conversation-1");
    }
}
