package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.Skill;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * SKILL技能处理器
 * 处理企业固定技能的多步骤任务
 */
@Component
public class SkillHandler implements AgentHandler {

    /**
     * 技能执行系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是技能执行助手，可以调用各种技能完成复杂任务。
            必须调用提供的技能工具完成任务，不能自行编造结果。
            如果现有技能无法满足需求，请明确说明当前支持的技能范围。
            使用中文简洁回答，并说明已执行的操作。
            """;

    private final ChatClient chatClient;
    private final List<Skill> skills;

    /**
     * 构造函数
     *
     * @param chatClientBuilder ChatClient构建器
     * @param memoryAdvisor     消息聊天记忆顾问
     * @param skills            技能列表
     */
    public SkillHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            List<Skill> skills
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();
        this.skills = skills;
    }

    /**
     * 获取处理器类型
     *
     * @return AgentType.SKILL
     */
    @Override
    public AgentType type() {
        return AgentType.SKILL;
    }

    /**
     * 处理技能执行消息
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果
     */
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