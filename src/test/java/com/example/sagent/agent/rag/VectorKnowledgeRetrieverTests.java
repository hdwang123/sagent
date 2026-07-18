package com.example.sagent.agent.rag;

import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VectorKnowledgeRetrieverTests {

    private final VectorKnowledgeRetriever retriever = new VectorKnowledgeRetriever(testEmbeddingModel());

    @Test
    void retrievesRoutingDocumentForRoutingQuestion() {
        assertThat(retriever.search("消息如何路由到 RAG 和数据库？"))
                .extracting(VectorKnowledgeRetriever.KnowledgeHit::source)
                .contains("agent-routing.md");
    }

    @Test
    void retrievesProjectDocumentForEnvironmentVariableQuestion() {
        assertThat(retriever.search("OPENROUTER_API_KEY 在哪里配置？"))
                .extracting(VectorKnowledgeRetriever.KnowledgeHit::source)
                .contains("sagent-overview.md");
    }

    private static EmbeddingModel testEmbeddingModel() {
        EmbeddingModel embeddingModel = mock(EmbeddingModel.class);
        when(embeddingModel.dimensions()).thenReturn(5);
        when(embeddingModel.embed(any(Document.class)))
                .thenAnswer(invocation -> vectorFor(invocation.<Document>getArgument(0).getText()));
        when(embeddingModel.embed(anyString()))
                .thenAnswer(invocation -> vectorFor(invocation.getArgument(0)));
        return embeddingModel;
    }

    private static float[] vectorFor(String text) {
        if (text.contains("OPENROUTER_API_KEY")) {
            return new float[]{1, 0, 0, 0, 0};
        }
        if (text.contains("路由")) {
            return new float[]{0, 1, 0, 0, 0};
        }
        if (text.contains("1998 SH2")) {
            return new float[]{0, 0, 1, 0, 0};
        }
        if (text.contains("beauty mesons")) {
            return new float[]{0, 0, 0, 1, 0};
        }
        if (text.contains("dementia")) {
            return new float[]{0, 0, 0, 0, 1};
        }
        return new float[]{1, 1, 1, 1, 1};
    }
}
