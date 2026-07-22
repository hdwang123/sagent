package com.example.sagent.agent.gskill;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用技能处理器（自由组合调用各种工具）
 */
@Component
public class GSkillAgentHandler implements AgentHandler {

    private static final String SYSTEM_PROMPT = """
            你是通用技能执行助手，可以调用各种技能完成复杂任务。
            必须调用提供的技能工具完成任务，不能自行编造结果。
            如果现有技能无法满足需求，请明确说明当前支持的技能范围。
            使用中文简洁回答，并说明已执行的操作。
            """;

    private final ChatClient chatClient;
    private final List<GSkill> skills;

    public GSkillAgentHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            List<GSkill> skills
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor,new SimpleLoggerAdvisor())
                .build();
        this.skills = skills;
    }

    @Override
    public AgentType type() {
        return AgentType.GSKILL;
    }

    @Override
    public HandlerResult handle(String conversationId, String message) {
        String answer = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(message)
                .tools(skills.toArray())
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();

        return new HandlerResult(answer);
    }
}