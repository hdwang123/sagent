package com.example.sagent.agent.rag;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.stream.Collectors;

@Component
public class RagAgentHandler implements AgentHandler {

    private static final String RAG_SYSTEM_PROMPT = """
            你是 Sagent 知识库助手。
            仅依据提供的知识库上下文回答问题，不要编造上下文中不存在的事实。
            如果上下文不足，请明确说明知识库中暂时没有相关信息。
            回答使用中文，并保持简洁。
            """;

    private final ChatClient chatClient;
    private final VectorKnowledgeRetriever knowledgeRetriever;

    public RagAgentHandler(
            ChatClient.Builder chatClientBuilder,
            VectorKnowledgeRetriever knowledgeRetriever
    ) {
        this.chatClient = chatClientBuilder.build();
        this.knowledgeRetriever = knowledgeRetriever;
    }

    @Override
    public AgentType type() {
        return AgentType.RAG;
    }

    @Override
    public HandlerResult handle(String message) {
        List<VectorKnowledgeRetriever.KnowledgeHit> hits = knowledgeRetriever.search(message);
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
                .call()
                .content();

        List<String> sources = hits.stream()
                .map(VectorKnowledgeRetriever.KnowledgeHit::source)
                .distinct()
                .toList();
        return new HandlerResult(answer, sources);
    }
}
