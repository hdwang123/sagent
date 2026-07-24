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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private final List<Document> documents;

    /**
     * 构造函数
     * 初始化向量库并加载知识库文档
     *
     * @param embeddingModel Embedding模型
     */
    public VectorKnowledgeRetriever(EmbeddingModel embeddingModel) {
        this.documents = loadDocuments();
        this.vectorStore = SimpleVectorStore.builder(embeddingModel).build();
        this.vectorStore.add(this.documents);
        LOGGER.info(
                "RAG 向量库初始化完成：embeddingModel={}, documents={}",
                embeddingModel.getClass().getSimpleName(),
                this.documents.size()
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
        return search(query, MAX_RESULTS);
    }

    public List<KnowledgeHit> search(String query, int topK) {
        SearchRequest request = SearchRequest.builder()
                .query(query)
                .topK(topK)
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
     * 关键词检索（内存模糊匹配）
     * 将查询分词后，按文档包含的匹配词数计分排序
     *
     * @param query 查询语句
     * @param topK  返回结果数
     * @return 检索结果列表
     */
    public List<KnowledgeHit> keywordSearch(String query, int topK) {
        String[] queryWords = query.toLowerCase().split("\\s+");
        List<KnowledgeHit> hits = new ArrayList<>();

        for (Document doc : this.documents) {
            String text = doc.getText().toLowerCase();
            String source = doc.getMetadata().get("source").toString();
            int matchCount = 0;
            for (String word : queryWords) {
                if (text.contains(word)) {
                    matchCount++;
                }
            }
            if (matchCount > 0) {
                double score = (double) matchCount / queryWords.length;
                hits.add(new KnowledgeHit(source, doc.getText(), score));
            }
        }

        hits.sort(Comparator.comparingDouble(KnowledgeHit::score).reversed());
        LOGGER.info("关键词检索完成：hits={}", hits.stream().map(h -> "%s(%.4f)".formatted(h.source(), h.score())).toList());
        return hits.stream().limit(topK).toList();
    }

    /**
     * 混合检索（向量 + 关键词，RRF融合）
     * 将两种检索结果按 Reciprocal Rank Fusion 合并去重排序
     *
     * @param query 查询语句
     * @param topK  返回结果数
     * @return 合并后的检索结果列表
     */
    public List<KnowledgeHit> hybridSearch(String query, int topK) {
        // 向量检索
        List<KnowledgeHit> vectorHits = search(query, topK * 3);
        // 关键词检索
        List<KnowledgeHit> keywordHits = keywordSearch(query, topK * 3);

        // RRF融合：每个来源取两侧得分的平均值
        Map<String, Double> combinedScores = new LinkedHashMap<>();
        for (int i = 0; i < vectorHits.size(); i++) {
            KnowledgeHit hit = vectorHits.get(i);
            double rrf = 1.0 / (60 + i + 1);
            combinedScores.merge(hit.source(), rrf, Double::sum);
        }
        for (int i = 0; i < keywordHits.size(); i++) {
            KnowledgeHit hit = keywordHits.get(i);
            double rrf = 1.0 / (60 + i + 1);
            combinedScores.merge(hit.source(), rrf, Double::sum);
        }

        // 构建去重后的结果列表（来源 -> 内容映射）
        Map<String, String> sourceToContent = new LinkedHashMap<>();
        for (KnowledgeHit hit : vectorHits) {
            sourceToContent.putIfAbsent(hit.source(), hit.content());
        }
        for (KnowledgeHit hit : keywordHits) {
            sourceToContent.putIfAbsent(hit.source(), hit.content());
        }

        List<KnowledgeHit> merged = combinedScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> new KnowledgeHit(e.getKey(), sourceToContent.getOrDefault(e.getKey(), ""), e.getValue()))
                .toList();

        LOGGER.info("混合检索完成：merged={}", merged.stream().map(h -> "%s(%.4f)".formatted(h.source(), h.score())).toList());
        return merged;
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