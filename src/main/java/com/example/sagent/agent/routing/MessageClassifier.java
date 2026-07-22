package com.example.sagent.agent.routing;

import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.RouteDecision;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class MessageClassifier {

    private static final String CLASSIFICATION_PROMPT_TEMPLATE = """
            你是专业的消息分类器，必须严格按照以下规则分类：

            分类规则：
            1. DATABASE：涉及产品查询、价格、库存、数量、分类等业务数据查询，仅返回数据不生成文件
               关键词：产品、价格、库存、数量、查询列表、统计
               示例："查产品列表" → DATABASE，"产品A价格" → DATABASE

            2. SKILL：需要生成报告、下载网页、压缩文件、文档操作等预定义流程
               关键词：生成报告、下载、压缩、保存文件、处理文件
               示例："生成产品报告" → SKILL，"下载网页" → SKILL

            3. RAG：需要检索内部文档、项目说明、使用手册、知识库内容
               关键词：Sagent、项目说明、路由规则、知识库、文档、手册
               示例："Sagent是什么" → RAG，"路由规则" → RAG

            4. GSKILL：需要设置闹钟、查询时间等工具调用
               关键词：闹钟、设置提醒、查询时间
               示例："设明天8点闹钟" → GSKILL，"现在几点" → GSKILL

            5. CHAT：其他所有情况，包括闲聊、写作、翻译、通用知识等

            严格按照优先级判断：DATABASE > SKILL > RAG > GSKILL > CHAT
            必须在type字段返回CHAT/RAG/DATABASE/SKILL/GSKILL之一，reason字段简要说明分类理由。
            """.trim();

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