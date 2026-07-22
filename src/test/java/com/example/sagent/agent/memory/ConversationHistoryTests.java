package com.example.sagent.agent.memory;

import com.example.sagent.agent.base.memory.ConversationHistory;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationHistoryTests {

    @Test
    void formatsHistoryAndAddsPreviousUserQuestionsToRetrievalQuery() {
        ChatMemory chatMemory = mock(ChatMemory.class);
        when(chatMemory.get("conversation-1")).thenReturn(List.of(
                new UserMessage("介绍一下 NASA 的那篇新闻"),
                new AssistantMessage("新闻讨论了小行星 1998 SH2。")
        ));
        ConversationHistory history = new ConversationHistory(chatMemory);

        assertThat(history.format("conversation-1"))
                .contains("用户：介绍一下 NASA 的那篇新闻")
                .contains("助手：新闻讨论了小行星 1998 SH2。");
        assertThat(history.retrievalQuery("conversation-1", "它为什么被重新分类？"))
                .isEqualTo("介绍一下 NASA 的那篇新闻\n它为什么被重新分类？");
    }
}
