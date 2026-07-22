package com.example.sagent.agent.routing;

import com.example.sagent.agent.base.memory.ConversationHistory;
import com.example.sagent.agent.base.model.AgentType;
import com.example.sagent.agent.base.model.RouteDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class MessageClassifier {

    private static final String CLASSIFICATION_PROMPT_TEMPLATE = """
            你是消息路由器，负责判断用户消息应进入哪个处理流程。
                        
            可用的处理类型：
            - CHAT：闲聊、写作、翻译、总结、通用知识或不需要访问本系统数据的问题。
            - RAG：询问 Sagent 项目说明、Agent 路由规则、内部知识文档、使用手册，
              或询问本地知识库收录的英文新闻内容。
            - DATABASE：需要查询产品数量、产品名称、价格、库存、分类等结构化业务数据，
              且仅需要查询数据，不需要生成文档或文件。
              示例："查一下产品列表"、"产品A的价格是多少"、"统计产品数量" → DATABASE
            - SKILL：需要执行多步骤任务，涉及多个工具的组合使用，如生成报告、下载网页、文件操作等。
            - GSKILL: 通用技能，自由组合多个工具使用，比如：查询时间、设置闹钟等
                        
            判断原则：
            1. 如果用户只问数据查询，选DATABASE；
            2. 如果需要查询后生成文件、或需要文件操作（生成、压缩等），选SKILL；
            3. 其他情况根据内容选择GSKILL或RAG或CHAT；
                        
                        
            返回格式要求：
            - 必须选择且只能选择 CHAT、RAG、DATABASE、SKILL、GSKILL 之一作为type；
            - 用简短中文说明reason。
            """;

    private final ChatClient chatClient;
    private final ConversationHistory conversationHistory;

    public MessageClassifier(
            ChatClient.Builder chatClientBuilder,
            ConversationHistory conversationHistory
    ) {
        this.chatClient = chatClientBuilder.build();
        this.conversationHistory = conversationHistory;
    }

    public RouteDecision classify(String conversationId, String message) {
        try {
            String history = conversationHistory.format(conversationId);
            String classificationInput = history.isBlank()
                    ? message
                    : """
                    以下是此前的会话，可用于理解当前消息中的指代和上下文：
                    %s

                    当前用户消息：
                    %s
                    """.formatted(history, message);

            RouteDecision decision = chatClient.prompt()
                    .system(CLASSIFICATION_PROMPT_TEMPLATE)
                    .user(classificationInput)
                    .call()
                    .entity(RouteDecision.class, spec -> spec.validateSchema());

            if (decision == null || decision.type() == null) {
                return fallbackDecision();
            }

            return decision;
        } catch (RuntimeException exception) {
            return fallbackDecision();
        }
    }

    private RouteDecision fallbackDecision() {
        return new RouteDecision(AgentType.CHAT, "分类模型未返回有效结果，已使用普通聊天兜底");
    }
}