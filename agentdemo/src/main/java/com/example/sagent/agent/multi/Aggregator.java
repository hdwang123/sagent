package com.example.sagent.agent.multi;

import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 多Agent汇总器
 * <p>
 * 将各子任务的执行结果整合为最终回答，强制保留下载链接。
 * 汇总失败时降级为拼接子任务结果原文，避免 null/NPE 导致整轮编排失败（P0-1）。
 */
@Component
public class Aggregator {

    private static final Logger LOGGER = LoggerFactory.getLogger(Aggregator.class);

    private static final String AGGREGATE_PROMPT = """
            你是结果汇总助手。以下是多个子Agent分别完成的任务结果，请将它们整合成一份完整、连贯、有条理的回答给用户。
            不要重复结果中已有的信息，按逻辑顺序组织，使用中文回答。
            **必须保留子任务结果中的所有下载链接（/files/download/开头的URL），原样放在回答末尾的"下载链接"部分，不要省略或改写。**

            {subResults}
            """;

    /** 下载链接提取正则（static final 避免每次重编译，P0-3） */
    private static final Pattern DOWNLOAD_LINK_PATTERN =
            Pattern.compile("(/files/download/[^\\s，。！？、；：）)}\\]]+)");

    private final ChatClient aggregateClient;

    public Aggregator(ChatClient.Builder chatClientBuilder) {
        this.aggregateClient = chatClientBuilder.build();
    }

    /**
     * 汇总Agent：将子任务结果整合为最终回答。
     * 展示时用子任务goal作为可读标签（而非id），便于汇总模型理解。
     * 汇总失败或返回空时降级为拼接子任务结果原文，保证不返回 null（P0-1）。
     *
     * @param message  原始用户请求
     * @param results  子任务结果
     * @param taskById id -> Task 映射，用于查goal作为可读标签
     * @return 汇总后的最终回答（非 null）
     */
    public String aggregate(String message, Map<String, HandlerResult> results, Map<String, Task> taskById) {
        String subResults = results.entrySet().stream()
                .map(e -> {
                    Task t = taskById.get(e.getKey());
                    String label = t == null ? e.getKey() : t.goal();
                    return "任务: " + label + "\n结果: " + e.getValue().answer();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
        try {
            String answer = aggregateClient.prompt()
                    .system(AGGREGATE_PROMPT)
                    .user(user -> user.text("用户原始请求：{message}\n\n子任务结果：\n{subResults}")
                            .param("message", message)
                            .param("subResults", subResults))
                    .call()
                    .content();
            if (answer == null || answer.isBlank()) {
                LOGGER.warn("汇总Agent返回空，降级为拼接子任务结果");
                return fallbackAnswer(results, taskById);
            }
            return answer;
        } catch (Exception e) {
            LOGGER.error("汇总Agent执行异常，降级为拼接子任务结果", e);
            return fallbackAnswer(results, taskById);
        }
    }

    /**
     * 降级回答：直接拼接子任务结果原文，保证不返回 null
     */
    private String fallbackAnswer(Map<String, HandlerResult> results, Map<String, Task> taskById) {
        return results.entrySet().stream()
                .map(e -> {
                    Task t = taskById.get(e.getKey());
                    String label = t == null ? e.getKey() : t.goal();
                    return "## " + label + "\n" + e.getValue().answer();
                })
                .collect(Collectors.joining("\n\n"));
    }

    /**
     * 从子任务结果中提取所有下载链接，用于兜底附加到汇总回答末尾。
     * 使用 static final Pattern 避免每次重编译（P0-3）。
     *
     * @param results 子任务结果
     * @return 去重后的下载链接列表
     */
    public List<String> extractDownloadLinks(Map<String, HandlerResult> results) {
        return results.values().stream()
                .map(r -> r.answer() == null ? "" : r.answer())
                .flatMap(answer -> DOWNLOAD_LINK_PATTERN.matcher(answer).results()
                        .map(m -> m.group(1)))
                .distinct()
                .toList();
    }
}
