package com.example.sagent.agent.multi;

import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.Task;
import com.example.sagent.agent.model.TaskPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 多Agent编排服务（演示版）
 * <p>
 * 门面层：组合 {@link Planner}（拆解/重新规划）、{@link TaskExecutor}（调度执行/纠偏）、
 * {@link Aggregator}（汇总）三个组件，职责仅为"拆解 -> 执行 -> 汇总"的编排串联，
 * 不含具体实现逻辑，便于各组件独立测试与替换（P2-10 拆分）。
 */
@Service
public class MultiAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiAgentService.class);

    private final Planner planner;
    private final TaskExecutor taskExecutor;
    private final Aggregator aggregator;

    public MultiAgentService(Planner planner, TaskExecutor taskExecutor, Aggregator aggregator) {
        this.planner = planner;
        this.taskExecutor = taskExecutor;
        this.aggregator = aggregator;
    }

    /**
     * 多Agent处理入口：Planner拆解 -> Executor执行 -> 汇总
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return 最终回答（含下载链接兜底；任一子任务失败时code标记为error）
     */
    public HandlerResult handle(String conversationId, String message) {
        // 1. Planner拆解任务（含多轮记忆 + 图校验，计划详情由 plan() 内部输出日志）
        long start = System.nanoTime();
        TaskPlan plan = planner.plan(conversationId, message);
        long planMs = (System.nanoTime() - start) / 1_000_000;

        // id -> Task 映射，供 execute/aggregate 按id查goal描述
        Map<String, Task> taskById = plan.tasks().stream()
                .collect(Collectors.toMap(Task::id, t -> t, (a, b) -> a, LinkedHashMap::new));

        // 2. Executor按依赖执行（结果以子任务id为键），Executor内部直接调Planner重新规划
        start = System.nanoTime();
        Map<String, HandlerResult> results = taskExecutor.execute(
                conversationId, plan.tasks(), taskById, message);
        long executeMs = (System.nanoTime() - start) / 1_000_000;

        // 3. 汇总Agent生成最终回答（含try-catch兜底，不会返回null）
        start = System.nanoTime();
        String answer = aggregator.aggregate(message, results, taskById);
        long aggregateMs = (System.nanoTime() - start) / 1_000_000;

        LOGGER.info("多Agent编排耗时: plan={}ms, execute={}ms, aggregate={}ms, total={}ms",
                planMs, executeMs, aggregateMs, planMs + executeMs + aggregateMs);

        // 4. 兜底：从子任务结果中提取下载链接，确保汇总遗漏时用户仍能下载
        List<String> downloadLinks = aggregator.extractDownloadLinks(results);
        List<String> sources = results.values().stream()
                .flatMap(r -> r.sources().stream())
                .distinct()
                .toList();
        if (!downloadLinks.isEmpty()) {
            String linkSection = "\n\n**下载链接：**\n" + String.join("\n", downloadLinks);
            if (!answer.contains("/files/download/")) {
                answer = answer + linkSection;
            }
        }
        // 整轮编排的 code：任一子任务失败则标记为 error，前端可据此展示
        int code = results.values().stream().anyMatch(HandlerResult::error)
                ? HandlerResult.CODE_ERROR : HandlerResult.CODE_SUCCESS;
        return new HandlerResult(answer, sources, code);
    }
}
