package com.example.sagent.agent.rag;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Component
public class VectorKnowledgeRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorKnowledgeRetriever.class);
    private static final int MAX_RESULTS = 3;
    private static final double SIMILARITY_THRESHOLD = 0.1;

    private final VectorStore vectorStore;

    public VectorKnowledgeRetriever(EmbeddingModel embeddingModel) {
        List<Document> documents = loadDocuments();
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.vectorStore.add(documents);
        LOGGER.info(
                "RAG 向量库初始化完成：embeddingModel={}, documents={}",
                embeddingModel.getClass().getSimpleName(),
                documents.size()
        );
    }

    public List<KnowledgeHit> search(String query) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(MAX_RESULTS)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();

        List<KnowledgeHit> hits = vectorStore.similaritySearch(request).stream()
                .map(document -> new KnowledgeHit(
                        document.getMetadata().get("source").toString(),
                        document.getText(),
                        document.getScore() == null ? 0 : document.getScore()
                ))
                .toList();
        LOGGER.info(
                "RAG 检索完成：hits={}",
                hits.stream()
                        .map(hit -> "%s(%.4f)".formatted(hit.source(), hit.score()))
                        .toList()
        );
        return hits;
    }

    private List<Document> loadDocuments() {
        try {
            Resource[] resources = new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:knowledge/*.md");
            List<Document> loaded = new ArrayList<>(resources.length);
            for (Resource resource : resources) {
                loaded.add(Document.builder()
                        .text(resource.getContentAsString(StandardCharsets.UTF_8))
                        .metadata("source", resource.getFilename())
                        .build());
            }
            return List.copyOf(loaded);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load knowledge documents", exception);
        }
    }

    public record KnowledgeHit(String source, String content, double score) {
    }
}
