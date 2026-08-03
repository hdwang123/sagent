package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.GSkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 通用技能处理器
 * 处理自由组合工具的通用技能任务
 */
@Component
public class GSkillHandler implements AgentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(GSkillHandler.class);

    /**
     * 通用技能执行系统提示词
     */
    private static final String SYSTEM_PROMPT = """
            你是通用技能执行助手，可以调用各种技能完成复杂任务。
            必须调用提供的技能工具完成任务，不能自行编造结果。
            【重要】每次查询数据库操作都必须重新调用工具获取最新数据，不能使用对话历史中的旧数据。
            如果现有技能无法满足需求，请明确说明当前支持的技能范围。
            使用中文简洁回答，并说明已执行的操作。
            """;

    private final ChatClient chatClient;
    private final List<GSkill> skills;

    /**
     * 构造函数
     *
     * @param chatClientBuilder ChatClient构建器
     * @param toolMemoryAdvisor 工具类小窗口记忆顾问（4条消息，防止LLM复述历史数据）
     * @param skills            通用技能列表
     */
    public GSkillHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("toolChatMemoryAdvisor") MessageChatMemoryAdvisor toolMemoryAdvisor,
            List<GSkill> skills
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(toolMemoryAdvisor, new SimpleLoggerAdvisor())
                .build();
        this.skills = skills;
    }

    /**
     * 获取处理器类型
     *
     * @return AgentType.GSKILL
     */
    @Override
    public AgentType type() {
        return AgentType.GSKILL;
    }

    /**
     * 处理通用技能执行消息
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果
     */
    @Override
    public HandlerResult handle(String conversationId, String message) {
        try {
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
        } catch (Exception e) {
            LOGGER.error("GSkillHandler处理失败", e);
            return new HandlerResult("通用技能执行失败：" + e.getMessage(), List.of(), true);
        }
    }
}