package com.example.sagent.agent.routing;

import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.RouteDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class MessageClassifier {

    private static final String CLASSIFICATION_PROMPT = """
            你是消息路由器，只负责判断用户消息应进入哪个处理流程。

            分类规则：
            - CHAT：闲聊、写作、翻译、总结、通用知识或不需要访问本系统数据的问题。
            - RAG：询问 Sagent 项目说明、Agent 路由规则、内部知识文档、使用手册，
              或询问本地知识库收录的英文新闻内容。
            - DATABASE：需要查询产品数量、产品名称、价格、库存、分类等结构化业务数据。

            必须选择且只能选择 CHAT、RAG、DATABASE 之一，并用简短中文说明理由。
            """;

    private final ChatClient chatClient;

    public MessageClassifier(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    public RouteDecision classify(String message) {
        try {
            RouteDecision decision = chatClient.prompt()
                    .system(CLASSIFICATION_PROMPT)
                    .user(message)
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
