package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.tools.VectorKnowledgeRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

/**
 * RAG检索处理器
 * 基于知识库内容回答用户问题
 */
@Component
public class RagHandler implements AgentHandler {

    /**
     * RAG系统提示词
     */
    private static final String RAG_SYSTEM_PROMPT = """
            你是 Sagent 知识库助手。
            仅依据提供的知识库上下文回答问题，不要编造上下文中不存在的事实。
            如果上下文不足，请明确说明知识库中暂时没有相关信息。
            回答使用中文，并保持简洁。
            """;

    private final ChatClient chatClient;
    private final VectorKnowledgeRetriever knowledgeRetriever;
    private final ConversationHistory conversationHistory;

    /**
     * 构造函数
     *
     * @param chatClientBuilder    ChatClient构建器
     * @param memoryAdvisor        消息聊天记忆顾问
     * @param knowledgeRetriever   向量知识库检索器
     * @param conversationHistory  会话历史管理
     */
    public RagHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            VectorKnowledgeRetriever knowledgeRetriever,
            ConversationHistory conversationHistory
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();
        this.knowledgeRetriever = knowledgeRetriever;
        this.conversationHistory = conversationHistory;
    }

    /**
     * 获取处理器类型
     *
     * @return AgentType.RAG
     */
    @Override
    public AgentType type() {
        return AgentType.RAG;
    }

    /**
     * 处理RAG检索消息
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return HandlerResult处理结果，包含回答和来源列表
     */
    @Override
    public HandlerResult handle(String conversationId, String message) {
        String retrievalQuery = conversationHistory.retrievalQuery(conversationId, message);
        List<VectorKnowledgeRetriever.KnowledgeHit> hits = knowledgeRetriever.search(retrievalQuery);
        String context = hits.isEmpty()
                ? "没有检索到相关知识库内容。"
                : hits.stream()
                        .map(hit -> "[来源: " + hit.source() + "]\n" + hit.content())
                        .collect(Collectors.joining("\n\n---\n\n"));

        String answer = chatClient.prompt()
                .system(RAG_SYSTEM_PROMPT)
                .user(user -> user.text("""
                                用户问题：
                                {question}

                                知识库上下文：
                                {context}
                                """)
                        .param("question", message)
                        .param("context", context))
                .advisors(advisor -> advisor.param(
                        ChatMemory.CONVERSATION_ID,
                        conversationId
                ))
                .call()
                .content();

        List<String> sources = hits.stream()
                .map(VectorKnowledgeRetriever.KnowledgeHit::source)
                .distinct()
                .toList();
        return new HandlerResult(answer, sources);
    }
}