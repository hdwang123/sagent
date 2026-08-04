package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentResult;
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

            【输出要求】调用工具并汇总结果后，必须输出如下 JSON 格式的结构化结果：
            {"code": 200, "content": "对用户问题的最终回答"}
            其中 code 取值约定：
            - 200：任务执行成功
            - 404：数据或资源不存在（如查询无结果）
            - 400：业务校验失败（如参数非法）
            - 500：工具执行出现技术错误
            如果工具返回了失败或错误，必须如实反映 code 和失败原因，不能伪造成功。

            【content 编写规则】content 是直接展示给用户的回答文本，必须满足：
            - 将工具返回的数据用中文自然语言整理成可读描述，如"共找到 2 款产品：1. iPhone 15，售价 5999 元，库存 120 件；2. ..."
            - 严禁将工具返回的原始 JSON、转义字符串（如 {"id":3,"name":"..."}）原样放入 content
            - 错误示例：{"code":200,"content":"{\"id\":3,\"name\":\"iPhone 15\"}"}
            - 正确示例：{"code":200,"content":"已查询到产品：iPhone 15，售价 5999 元，库存 120 件。"}
            - 简洁明了，说明已执行的操作和关键结果。
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
            AgentResult result = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .tools(skills.toArray())
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            conversationId
                    ))
                    .call()
                    .entity(AgentResult.class);

            if (result == null) {
                return new HandlerResult("", List.of(), HandlerResult.CODE_SUCCESS);
            }
            return new HandlerResult(result.content(), List.of(), result.code());
        } catch (Exception e) {
            LOGGER.error("GSkillHandler处理失败", e);
            return new HandlerResult("通用技能执行失败：" + e.getMessage(), List.of(), HandlerResult.CODE_ERROR);
        }
    }
}