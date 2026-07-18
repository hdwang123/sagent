package com.example.sagent;

import com.example.sagent.agent.database.ProductDatabaseTools;
import com.example.sagent.agent.rag.VectorKnowledgeRetriever;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class SagentApplicationTests {

    @Autowired
    private ProductDatabaseTools productDatabaseTools;

    @Autowired
    private EmbeddingModel embeddingModel;

    @Autowired
    private VectorKnowledgeRetriever knowledgeRetriever;

    @Test
    void contextLoads() {
    }

    @Test
    void databaseToolsQuerySeedData() {
        assertThat(productDatabaseTools.countProducts()).isEqualTo(5);
        assertThat(productDatabaseTools.findProductsByMaxPrice(new BigDecimal("70")))
                .extracting(product -> product.name())
                .containsExactly("Spring AI 开发手册");
    }

    @Test
    void ragUsesTransformersAndRetrievesKnowledgeDocument() {
        assertThat(embeddingModel).isInstanceOf(TransformersEmbeddingModel.class);
        assertFirstRagSource("OPENROUTER_API_KEY 在哪里配置？", "sagent-overview.md");
        assertFirstRagSource(
                "Why was 1998 SH2 reclassified as a comet?",
                "news-nasa-comet.md"
        );
        assertFirstRagSource(
                "How did CMS use beauty mesons to study matter-antimatter differences?",
                "news-cern-antimatter.md"
        );
        assertFirstRagSource(
                "What does WHO recommend to reduce dementia risk?",
                "news-who-dementia.md"
        );
    }

    private void assertFirstRagSource(String query, String expectedSource) {
        assertThat(knowledgeRetriever.search(query))
                .first()
                .satisfies(hit -> {
                    assertThat(hit.source()).isEqualTo(expectedSource);
                    assertThat(hit.score()).isPositive();
                });
    }
}
