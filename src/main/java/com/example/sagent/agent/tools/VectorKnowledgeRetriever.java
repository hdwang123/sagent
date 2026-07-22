package com.example.sagent.agent.tools;

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

/**
 * 向量知识库检索器
 * 基于向量相似度检索知识库内容
 */
@Component
public class VectorKnowledgeRetriever {

    private static final Logger LOGGER = LoggerFactory.getLogger(VectorKnowledgeRetriever.class);

    /**
     * 最大检索结果数
     */
    private static final int MAX_RESULTS = 3;

    /**
     * 相似度阈值
     */
    private static final double SIMILARITY_THRESHOLD = 0.1;

    private final VectorStore vectorStore;

    /**
     * 构造函数
     * 初始化向量库并加载知识库文档
     *
     * @param embeddingModel Embedding模型
     */
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

    /**
     * 搜索知识库
     * 根据查询语句检索相关知识库内容
     *
     * @param query 查询语句
     * @return 检索结果列表
     */
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

    /**
     * 加载知识库文档
     * 从classpath加载所有markdown格式的知识库文档
     *
     * @return 文档列表
     */
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

    /**
     * 检索结果记录
     */
    public record KnowledgeHit(String source, String content, double score) {
    }
}