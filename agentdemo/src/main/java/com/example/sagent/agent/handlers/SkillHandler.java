package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentResultParser;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.Skill;
import tools.jackson.databind.ObjectMapper;
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
 * 企业技能处理器
 * <p>
 * 处理固定技能任务（如网页下载、文档生成）。
 * 提示词约束 LLM 单次只调用一个工具（框架仍走工具调用循环），
 * 工具通过 {@code @Tool(returnDirect=true)} 直接返回 {@link com.example.sagent.agent.model.AgentResult} 的 JSON 字符串，
 * 由 {@link AgentResultParser} 反序列化为 {@link HandlerResult}。
 */
@Component
public class SkillHandler implements AgentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(SkillHandler.class);

    private static final String SYSTEM_PROMPT = """
            你是技能执行助手。分析用户请求，找到一个最合适的工具即可调用，不要调用多个工具。

            如果没有合适的工具，直接回答用户问题，不需要强行调用工具。

            工具调用完成后，用中文总结结果给用户。**必须保留工具返回的所有下载链接（/files/download/开头的URL），不要省略或改写**。

            重要：如果用户要求"生成/创建/保存一份文档或文件"，必须调用 generateMarkdownDocument 等生成类工具创建**新文件**并返回下载链接；不要因为output目录下已有相似文件而改用readDocument读取旧文件来应付。
            """;

    private final ChatClient chatClient;
    private final List<Skill> skills;
    private final ObjectMapper objectMapper;

    /**
     * 构造函数
     *
     * @param chatClientBuilder ChatClient构建器
     * @param toolMemoryAdvisor 工具类小窗口记忆顾问（4条消息，防止LLM复述历史数据）
     * @param skills            企业技能列表
     * @param objectMapper      Jackson ObjectMapper（用于 returnDirect 场景的 AgentResult JSON 解析）
     */
    public SkillHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("toolChatMemoryAdvisor") MessageChatMemoryAdvisor toolMemoryAdvisor,
            List<Skill> skills,
            ObjectMapper objectMapper
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(toolMemoryAdvisor, new SimpleLoggerAdvisor())
                .build();
        this.skills = skills;
        this.objectMapper = objectMapper;
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
     * 处理企业技能消息
     * <p>
     * 工具以 {@code @Tool(returnDirect=true)} 声明，调用后直接返回 {@link AgentResult} 的 JSON 字符串，
     * 由 {@link AgentResultParser#toHandlerResult} 解析为 {@link HandlerResult}（含 code/content 提取）。
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果
     */
    @Override
    public HandlerResult handle(String conversationId, String message) {
        try {
            String raw = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(message)
                    .tools(skills.toArray())
                    .advisors(advisor -> advisor.param(
                            ChatMemory.CONVERSATION_ID,
                            conversationId
                    ))
                    .call()
                    .content();

            return AgentResultParser.toHandlerResult(objectMapper, raw);
        } catch (Exception e) {
            LOGGER.error("SkillHandler处理失败", e);
            return new HandlerResult("技能执行失败：" + e.getMessage(), List.of(), HandlerResult.CODE_ERROR);
        }
    }
}
