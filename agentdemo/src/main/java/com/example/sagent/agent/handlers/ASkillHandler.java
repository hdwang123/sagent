package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.ASkill;
import com.example.sagent.agent.skills.ApprovalContext;
import com.example.sagent.agent.approval.UserIdResolver;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ASkillHandler implements AgentHandler {

    private static final String SYSTEM_PROMPT = """
            你是审批技能执行助手。
            你可以执行以下两种操作：

            1. 【查询审批状态】使用 getMyApprovals() 或 checkApprovalById(审批编号) 实时查询审批记录的状态。
            2. 【提交审批操作】使用 deleteProduct/updateProductPrice/updateProductStock 提交需要审批的敏感操作。

            如果返回 PENDING:n 格式，说明操作已提交审批请求，告知用户审批编号并提示在审批面板中操作。
            用户可以随时使用查询工具查看审批状态。
            使用中文简洁回答。
            """;

    private final ChatClient chatClient;
    private final List<ASkill> skills;
    private final UserIdResolver userIdResolver;

    public ASkillHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("toolChatMemoryAdvisor") MessageChatMemoryAdvisor toolMemoryAdvisor,
            List<ASkill> skills,
            UserIdResolver userIdResolver
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(toolMemoryAdvisor, new SimpleLoggerAdvisor())
                .build();
        this.skills = skills;
        this.userIdResolver = userIdResolver;
    }

    @Override
    public AgentType type() {
        return AgentType.ASKILL;
    }

    @Override
    public HandlerResult handle(String conversationId, String message) {
        try {
            ApprovalContext.setConversationId(conversationId);
            ApprovalContext.setUserId(userIdResolver.resolve(conversationId));

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
        } finally {
            ApprovalContext.clear();
        }
    }
}
