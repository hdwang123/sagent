package com.example.sagent.agent.handlers;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentResult;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.tools.VectorKnowledgeRetriever;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class RagHandler implements AgentHandler {

    private static final String RAG_SYSTEM_PROMPT = """
            你是 Sagent 知识库助手。
            仅依据提供的知识库上下文回答问题，不要编造上下文中不存在的事实。
            如果上下文不足，请明确说明知识库中暂时没有相关信息。
            回答使用中文，并保持简洁。
            """;

    private static final String RERANK_PROMPT = """
            评估以下文档与问题的相关性，为每个文档打分（0-10的整数，10表示最相关）。
            每行输出一个分数，格式为：序号:分数
            只输出分数行，不要其他内容。
            
            问题：{question}
            文档列表：
            {documents}
            """;

    private static final Logger LOGGER = LoggerFactory.getLogger(RagHandler.class);

    private final int hybridTopK;
    private final int rerankedTopK;
    private final ChatClient chatClient;
    private final ChatClient rerankClient;
    private final VectorKnowledgeRetriever knowledgeRetriever;
    private final ConversationHistory conversationHistory;

    public RagHandler(
            ChatClient.Builder chatClientBuilder,
            @Qualifier("messageChatMemoryAdvisor") MessageChatMemoryAdvisor memoryAdvisor,
            VectorKnowledgeRetriever knowledgeRetriever,
            ConversationHistory conversationHistory,
            ChatModel chatModel,
            @Value("${agent.rag.hybrid-top-k:10}") int hybridTopK,
            @Value("${agent.rag.reranked-top-k:3}") int rerankedTopK
    ) {
        this.hybridTopK = hybridTopK;
        this.rerankedTopK = rerankedTopK;
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
        try {
            String retrievalQuery = conversationHistory.retrievalQuery(conversationId, message);

            // 1. 混合检索：向量 + 关键词，召回 Top-10
            List<VectorKnowledgeRetriever.KnowledgeHit> hybridHits = knowledgeRetriever.hybridSearch(retrievalQuery, hybridTopK);

            List<VectorKnowledgeRetriever.KnowledgeHit> hits = llmRerank(message, hybridHits, rerankedTopK);

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
            // P1-5: 检索为空时返回 404（业务失败），让编排层能识别 RAG 软失败而非当作成功
            int code = sources.isEmpty() ? AgentResult.CODE_NOT_FOUND : HandlerResult.CODE_SUCCESS;
            return new HandlerResult(answer, sources, code);
        } catch (Exception e) {
            LOGGER.error("RagHandler处理失败", e);
            return new HandlerResult("知识库检索失败：" + e.getMessage(), List.of(), HandlerResult.CODE_ERROR);
        }
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

            // 按分数重排序：以索引排序，避免 indexOf 在重复对象上取错位置
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < candidates.size(); i++) {
                order.add(i);
            }
            order.sort((a, b) -> Integer.compare(
                    b < scores.size() ? scores.get(b) : 0,
                    a < scores.size() ? scores.get(a) : 0
            ));

            return order.stream()
                    .limit(topK)
                    .map(candidates::get)
                    .toList();
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