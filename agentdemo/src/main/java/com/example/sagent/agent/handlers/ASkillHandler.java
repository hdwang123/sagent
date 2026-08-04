package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentResult;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.skills.ASkill;
import com.example.sagent.agent.skills.ApprovalContext;
import com.example.sagent.agent.approval.UserIdResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ASkillHandler implements AgentHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ASkillHandler.class);

    private static final String SYSTEM_PROMPT = """
            你是审批技能执行助手。
            你可以执行以下两种操作：

            1. 【查询审批状态】使用 getMyApprovals() 或 checkApprovalById(审批编号) 实时查询审批记录的状态。
            2. 【提交审批操作】使用 deleteProduct/updateProductPrice/updateProductStock 提交需要审批的敏感操作。

            如果返回 PENDING:n 格式，说明操作已提交审批请求，告知用户审批编号并提示在审批面板中操作。
            用户可以随时使用查询工具查看审批状态。

            【输出要求】调用工具并汇总结果后，输出结构化结果（code + content），其中 code 取值约定：
            - 200：操作成功（包括已提交审批、查询到状态）
            - 404：审批记录或数据不存在
            - 400：业务校验失败（如参数非法）
            - 500：工具执行出现技术错误
            如果工具返回了失败或错误，必须如实反映 code 和失败原因，不能伪造成功。
            【content 编写规则】content 是直接展示给用户的回答文本：
            - 用中文自然语言整理工具返回的数据，严禁将原始 JSON 或转义字符串原样放入 content
            - 错误示例：{"code":200,"content":"{\"id\":3,\"name\":\"iPhone 15\"}"}
            - 正确示例：{"code":200,"content":"审批单 A-2026-001 当前状态为：待审批。"}
            - 简洁明了，告知用户操作结果或审批状态。
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
            LOGGER.error("ASkillHandler处理失败", e);
            return new HandlerResult("审批技能执行失败：" + e.getMessage(), List.of(), HandlerResult.CODE_ERROR);
        } finally {
            ApprovalContext.clear();
        }
    }
}
