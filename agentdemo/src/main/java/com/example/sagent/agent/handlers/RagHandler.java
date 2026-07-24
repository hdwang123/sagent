package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.tools.VectorKnowledgeRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

    /**
     * LLM重排序提示词
     */
    private static final String RERANK_PROMPT = """
            评估以下文档与问题的相关性，为每个文档打分（0-10的整数，10表示最相关）。
            每行输出一个分数，格式为：序号:分数
            只输出分数行，不要其他内容。
            
            问题：{question}
            文档列表：
            {documents}
            """;

    private static final Logger LOGGER = LoggerFactory.getLogger(RagHandler.class);

    private static final int HYBRID_TOP_K = 10;
    private static final int RERANKED_TOP_K = 3;

    private final ChatClient chatClient;
    private final ChatClient rerankClient;
    private final VectorKnowledgeRetriever knowledgeRetriever;
    private final ConversationHistory conversationHistory;

    /**
     * 构造函数
     *
     * @param chatClientBuilder    ChatClient构建器
     * @param memoryAdvisor        消息聊天记忆顾问
     * @param knowledgeRetriever   向量知识库检索器
     * @param conversationHistory  会话历史管理
     * @param chatModel            聊天模型
     */
    public RagHandler(
            ChatClient.Builder chatClientBuilder,
            MessageChatMemoryAdvisor memoryAdvisor,
            VectorKnowledgeRetriever knowledgeRetriever,
            ConversationHistory conversationHistory,
            ChatModel chatModel
    ) {
        this.chatClient = chatClientBuilder
                .defaultAdvisors(memoryAdvisor)
                .build();
        this.rerankClient = ChatClient.builder(chatModel).build();
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

        // 1. 混合检索：向量 + 关键词，召回 Top-10
        List<VectorKnowledgeRetriever.KnowledgeHit> hybridHits = knowledgeRetriever.hybridSearch(retrievalQuery, HYBRID_TOP_K);

        // 2. LLM 重排序：精排选出 Top-3
        List<VectorKnowledgeRetriever.KnowledgeHit> hits = llmRerank(message, hybridHits, RERANKED_TOP_K);

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

    /**
     * LLM 重排序
     * 用LLM对混合检索候选文档打分，返回Top-K
     */
    private List<VectorKnowledgeRetriever.KnowledgeHit> llmRerank(
            String question,
            List<VectorKnowledgeRetriever.KnowledgeHit> candidates,
            int topK
    ) {
        if (candidates.size() <= topK) {
            return candidates;
        }

        StringBuilder docList = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            var hit = candidates.get(i);
            docList.append(i + 1).append(". [").append(hit.source()).append("] ")
                    .append(hit.content().substring(0, Math.min(hit.content().length(), 200)))
                    .append("\n");
        }

        try {
            String response = rerankClient.prompt()
                    .user(user -> user.text(RERANK_PROMPT)
                            .param("question", question)
                            .param("documents", docList.toString()))
                    .call()
                    .content();

            // 解析LLM返回的分数
            List<Integer> scores = parseScores(response, candidates.size());

            // 按分数重排序
            List<VectorKnowledgeRetriever.KnowledgeHit> ranked = new ArrayList<>(candidates);
            ranked.sort((a, b) -> {
                int idxA = candidates.indexOf(a);
                int idxB = candidates.indexOf(b);
                return Integer.compare(
                        idxB < scores.size() ? scores.get(idxB) : 0,
                        idxA < scores.size() ? scores.get(idxA) : 0
                );
            });

            return ranked.stream().limit(topK).toList();
        } catch (Exception e) {
            LOGGER.warn("LLM重排序失败，使用原始混合检索结果: {}", e.getMessage());
            return candidates.stream().limit(topK).toList();
        }
    }

    /**
     * 从LLM响应中解析分数列表
     */
    private List<Integer> parseScores(String response, int expectedSize) {
        List<Integer> scores = new ArrayList<>();
        java.util.regex.Matcher matcher = Pattern.compile("(\\d+)\\s*:\\s*(\\d+)").matcher(response);
        while (matcher.find()) {
            scores.add(Integer.parseInt(matcher.group(2)));
        }
        return scores;
    }
}