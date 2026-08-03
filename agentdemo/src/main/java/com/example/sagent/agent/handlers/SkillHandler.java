package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.Skill;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SkillHandler implements AgentHandler {

    private static final String SYSTEM_PROMPT = """
            你是技能执行助手。分析用户请求，找到一个最合适的工具即可调用，不要调用多个工具。

            如果没有合适的工具，直接回答用户问题，不需要强行调用工具。

            工具调用完成后，用中文总结结果给用户。**必须保留工具返回的所有下载链接（/files/download/开头的URL），不要省略或改写**。

            重要：如果用户要求"生成/创建/保存一份文档或文件"，必须调用 generateMarkdownDocument 等生成类工具创建**新文件**并返回下载链接；不要因为output目录下已有相似文件而改用readDocument读取旧文件来应付。
            """;

    private final ChatClient chatClient;
    private final List<Skill> skills;

    public SkillHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("toolChatMemoryAdvisor") MessageChatMemoryAdvisor toolMemoryAdvisor,
            List<Skill> skills
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(toolMemoryAdvisor, new SimpleLoggerAdvisor())
                .build();
        this.skills = skills;
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