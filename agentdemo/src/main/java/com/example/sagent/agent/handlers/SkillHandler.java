package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.WebPageDownloadSkill;
import com.example.sagent.agent.tools.CompressionTool;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

@Component
public class SkillHandler implements AgentHandler {

    private static final String SYSTEM_PROMPT = """
            你是技能执行助手。分析用户请求，找到一个最合适的工具即可调用，不要调用多个工具。
            
            如果没有合适的工具，直接回答用户问题，不需要强行调用工具。
            
            工具调用完成后，直接用中文总结结果给用户。
            """;

    private final ChatClient chatClient;
    private final WebPageDownloadSkill webPageDownloadSkill;

    public SkillHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            WebPageDownloadSkill webPageDownloadSkill
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor, new SimpleLoggerAdvisor())
                .build();
        this.webPageDownloadSkill = webPageDownloadSkill;
    }

    @Override
    public AgentType type() {
        return AgentType.SKILL;
    }

    @Override
    public HandlerResult handle(String conversationId, String message) {
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .tools(webPageDownloadSkill)
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();

        return new HandlerResult(answer);
    }
}