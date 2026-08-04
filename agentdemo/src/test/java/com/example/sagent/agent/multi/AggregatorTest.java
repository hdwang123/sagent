package com.example.sagent.agent.multi;

import com.example.sagent.agent.model.HandlerResult;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Aggregator 单元测试
 * <p>
 * 覆盖 extractDownloadLinks 的链接提取、去重、中文标点截断、null 安全等纯逻辑路径。
 */
class AggregatorTest {

    private Aggregator createAggregator() {
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        when(mockBuilder.build()).thenReturn(mock(ChatClient.class));
        return new Aggregator(mockBuilder);
    }

    @Test
    void extractLinks_singleLink_extracted() {
        Aggregator agg = createAggregator();
        Map<String, HandlerResult> results = Map.of(
                "t1", new HandlerResult("文档已生成：/files/download/doc/test.md"));
        List<String> links = agg.extractDownloadLinks(results);
        assertThat(links).containsExactly("/files/download/doc/test.md");
    }

    @Test
    void extractLinks_multipleLinksInOneAnswer_extractedAll() {
        Aggregator agg = createAggregator();
        Map<String, HandlerResult> results = Map.of(
                "t1", new HandlerResult("文件1：/files/download/a/1.md 文件2：/files/download/b/2.png"));
        List<String> links = agg.extractDownloadLinks(results);
        assertThat(links).containsExactlyInAnyOrder("/files/download/a/1.md", "/files/download/b/2.png");
    }

    @Test
    void extractLinks_duplicateLinks_deduped() {
        Aggregator agg = createAggregator();
        Map<String, HandlerResult> results = Map.of(
                "t1", new HandlerResult("/files/download/doc/test.md"),
                "t2", new HandlerResult("/files/download/doc/test.md"));
        List<String> links = agg.extractDownloadLinks(results);
        assertThat(links).hasSize(1);
    }

    @Test
    void extractLinks_noLink_emptyList() {
        Aggregator agg = createAggregator();
        Map<String, HandlerResult> results = Map.of(
                "t1", new HandlerResult("这是普通文本，没有下载链接"));
        List<String> links = agg.extractDownloadLinks(results);
        assertThat(links).isEmpty();
    }

    @Test
    void extractLinks_nullAnswer_noError() {
        Aggregator agg = createAggregator();
        Map<String, HandlerResult> results = Map.of(
                "t1", new HandlerResult((String) null));
        List<String> links = agg.extractDownloadLinks(results);
        assertThat(links).isEmpty();
    }

    @Test
    void extractLinks_chinesePunctuationAfterLink_stoppedAtPunctuation() {
        Aggregator agg = createAggregator();
        Map<String, HandlerResult> results = Map.of(
                "t1", new HandlerResult("下载地址：/files/download/doc/test.md，请点击"));
        List<String> links = agg.extractDownloadLinks(results);
        assertThat(links).containsExactly("/files/download/doc/test.md");
    }

    @Test
    void extractLinks_multiSegmentPath_extracted() {
        Aggregator agg = createAggregator();
        Map<String, HandlerResult> results = Map.of(
                "t1", new HandlerResult("图片：/files/download/folder/sub/image.png"));
        List<String> links = agg.extractDownloadLinks(results);
        assertThat(links).containsExactly("/files/download/folder/sub/image.png");
    }

    @Test
    void extractLinks_emptyResults_emptyList() {
        Aggregator agg = createAggregator();
        List<String> links = agg.extractDownloadLinks(Map.of());
        assertThat(links).isEmpty();
    }
}
