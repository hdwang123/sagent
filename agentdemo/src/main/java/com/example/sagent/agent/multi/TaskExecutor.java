package com.example.sagent.agent.multi;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.core.HandlerRegistry;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * 多Agent执行器
 * <p>
 * 按依赖关系分波次调度执行子任务，无依赖任务并行执行。
 * 含三层兜底（异常/超时/死锁）与失败纠偏（重试/重新规划/止损）。
 * 重新规划直接调用 {@link Planner}，Executor 持有 Planner 引用。
 */
@Component
public class TaskExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskExecutor.class);

    /**
     * 单个子Agent执行超时阈值（秒），超时后整轮编排不再等待该任务，直接返回错误结果
     */
    private static final long SUB_AGENT_TIMEOUT_SECONDS = 60L;

    /**
     * 单个子任务失败后的重试次数（不含首次执行），重试时把上次失败原因拼入goal提示模型换种方式。
     * 仅对 5xx 技术错误重试，4xx 业务失败（如资源不存在）重试无意义直接返回（P1-4）。
     */
    private static final int MAX_RETRY = 1;

    /**
     * 失败后允许重新规划的最大次数，用完后改为依赖止损（跳过受影响的后续任务）
     */
    private static final int MAX_REPLAN = 2;

    private final HandlerRegistry handlerRegistry;
    private final Planner planner;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    public TaskExecutor(HandlerRegistry handlerRegistry, Planner planner) {
        this.handlerRegistry = handlerRegistry;
        this.planner = planner;
    }

    /**
     * 优雅关闭线程池，避免应用关停时丢任务（P2-10 工程化改进）
     */
    @jakarta.annotation.PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Executor：按依赖关系分波次执行子任务，无依赖的任务并行执行。
     * 结果以子任务 id 为键；单个子任务异常或超时不会中断整轮编排，会降级为错误结果；
     * 出现循环依赖或依赖id不存在时，剩余任务标记失败跳过，不盲目执行。
     *
     * @param conversationId 会话ID
     * @param tasks          子任务列表
     * @param taskById       id -> Task 映射，供 runSubAgent 按依赖id查goal描述
     * @param message        原始用户消息（供重新规划使用）
     * @return 子任务id -> 执行结果
     */
    public Map<String, HandlerResult> execute(String conversationId, List<Task> tasks,
                                                Map<String, Task> taskById, String message) {
        Map<String, HandlerResult> results = new LinkedHashMap<>();
        List<Task> pending = new ArrayList<>(tasks);
        int replanCount = 0;
        int wave = 0;
        LOGGER.info("Executor启动: 共{}个子任务待执行", tasks.size());

        while (!pending.isEmpty()) {
            wave++;
            LOGGER.info("===== 波次{}开始: pending={}个 =====", wave, pending.size());
            // 找出本轮可执行的任务（无依赖 或 所有依赖已完成）
            List<Task> ready = pending.stream()
                    .filter(t -> t.dependsOn() == null || t.dependsOn().isEmpty()
                            || t.dependsOn().stream().allMatch(results::containsKey))
                    .toList();
            if (ready.isEmpty()) {
                // 死锁防护：存在循环依赖或依赖指向不存在的任务id，剩余任务永远无法满足依赖。
                LOGGER.warn("依赖无法满足（循环依赖或依赖id不存在），剩余任务标记失败跳过: {}",
                        pending.stream().map(t -> t.id() + ":" + t.goal()).toList());
                markFailed(results, taskById,
                        pending.stream().map(Task::id).collect(Collectors.toSet()),
                        "因依赖无法满足而跳过（循环依赖或依赖id不存在）");
                break;
            }
            LOGGER.info("波次{}就绪任务: {}", wave, ready.stream().map(Task::id).toList());
            pending.removeAll(ready);

            // 本轮并行执行：每个子任务加超时与异常兜底
            List<CompletableFuture<Map.Entry<String, HandlerResult>>> futures = ready.stream()
                    .map(task -> scheduleTask(conversationId, task, results, taskById))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            futures.forEach(f -> {
                var entry = f.join();
                results.put(entry.getKey(), entry.getValue());
            });
            long successCount = ready.stream().filter(t -> !results.get(t.id()).error()).count();
            long failCount = ready.size() - successCount;
            LOGGER.info("波次{}执行完成: 成功{}个, 失败{}个", wave, successCount, failCount);

            // 纠偏：检查失败任务，尝试重新规划或止损
            if (!pending.isEmpty()) {
                Set<String> failedIds = results.entrySet().stream()
                        .filter(e -> e.getValue().error())
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toSet());
                if (!failedIds.isEmpty()) {
                    if (replanCount < MAX_REPLAN) {
                        // 方案B：失败触发重新规划
                        replanCount++;
                        LOGGER.info("检测到{}个失败任务，触发第{}/{}次重新规划",
                                failedIds.size(), replanCount, MAX_REPLAN);
                        List<Task> newTasks = planner.replan(message, results, failedIds, pending, taskById);
                        // P0-2: 清理失败任务的旧 id，避免 LLM 复用 id 时 taskById 污染
                        failedIds.forEach(taskById::remove);
                        pending.clear();
                        pending.addAll(newTasks);
                        for (Task t : newTasks) {
                            taskById.put(t.id(), t);
                        }
                        LOGGER.info("重新规划完成: 新增{}个任务, 剩余pending={}个", newTasks.size(), pending.size());
                        continue;
                    }
                    // 方案E：重新规划次数用完，依赖失败任务的后续任务注定无意义，止损跳过
                    LOGGER.warn("重新规划次数用完({})，对依赖失败任务的剩余任务执行止损跳过", MAX_REPLAN);
                    Set<String> toFail = new HashSet<>();
                    boolean changed = true;
                    while (changed) {
                        changed = false;
                        for (Task t : pending) {
                            if (toFail.contains(t.id())) {
                                continue;
                            }
                            if (t.dependsOn() != null && t.dependsOn().stream()
                                    .anyMatch(d -> failedIds.contains(d) || toFail.contains(d))) {
                                toFail.add(t.id());
                                changed = true;
                            }
                        }
                    }
                    markFailed(results, taskById, toFail, "因依赖任务失败而止损跳过");
                    pending.removeIf(t -> toFail.contains(t.id()));
                    LOGGER.info("止损完成: 跳过{}个依赖失败链任务, 剩余pending={}个", toFail.size(), pending.size());
                }
            }
        }
        long totalFail = results.values().stream().filter(HandlerResult::error).count();
        LOGGER.info("Executor结束: 共完成{}个子任务, 其中失败{}个", results.size(), totalFail);
        return results;
    }

    /**
     * 止损公共逻辑：把指定id集合的子任务标记为失败（写入error结果）。
     */
    private void markFailed(Map<String, HandlerResult> results,
                            Map<String, Task> taskById, Set<String> ids, String reason) {
        for (String id : ids) {
            Task t = taskById.get(id);
            String label = t == null ? id : t.goal();
            results.put(id, new HandlerResult("子任务[" + label + "]" + reason, List.of(), HandlerResult.CODE_ERROR));
        }
    }

    /**
     * 异步调度单个子任务，附带超时与异常兜底。
     */
    private CompletableFuture<Map.Entry<String, HandlerResult>> scheduleTask(
            String conversationId, Task task,
            Map<String, HandlerResult> results, Map<String, Task> taskById) {
        return CompletableFuture.supplyAsync(
                        () -> Map.entry(task.id(), runSubAgent(conversationId, task, results, taskById)),
                        executor)
                .orTimeout(SUB_AGENT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .exceptionally(ex -> {
                    Throwable root = (ex instanceof CompletionException && ex.getCause() != null)
                            ? ex.getCause() : ex;
                    String msg = root instanceof TimeoutException
                            ? "子任务执行超时（>" + SUB_AGENT_TIMEOUT_SECONDS + "s）"
                            : "子任务执行失败：" + root.getMessage();
                    LOGGER.error("子Agent[{}] {}", task.type(), msg, root);
                    return Map.entry(task.id(), new HandlerResult(
                            "子任务[" + task.goal() + "]" + msg, List.of(), HandlerResult.CODE_ERROR));
                });
    }

    /**
     * 执行单个子任务：复用现有Handler，使用独立会话ID避免污染主会话。
     * 若任务声明了依赖，将所有依赖任务的执行结果一并拼入goal。
     * 整个方法体被 try-catch 包裹：任何异常都降级为错误结果返回，不向上抛出。
     * P1-4: 4xx 业务失败（如资源不存在）重试无意义，直接返回；仅 5xx 技术错误才重试。
     */
    private HandlerResult runSubAgent(String conversationId, Task task,
                                      Map<String, HandlerResult> results, Map<String, Task> taskById) {
        String baseGoal = buildGoalWithDeps(task, results, taskById);
        HandlerResult lastResult = null;
        int maxAttempts = 1 + MAX_RETRY;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String goal = baseGoal;
            if (attempt > 1 && lastResult != null) {
                goal = baseGoal + "\n\n【重试提示】上次执行失败：" + lastResult.answer()
                        + "\n请分析失败原因，换一种方式完成任务。";
            }
            try {
                AgentHandler handler = handlerRegistry.get(task.type());
                if (handler == null) {
                    LOGGER.warn("无处理器: {}, 子任务[{}]降级为普通聊天", task.type(), task.goal());
                    return handlerRegistry.get(AgentType.CHAT).handle(conversationId, goal);
                }
                String subConversationId = UUID.randomUUID().toString();
                LOGGER.info("子Agent[{}, 会话{}] 第{}/{}次执行: {}",
                        task.type(), subConversationId, attempt, maxAttempts, task.goal());
                HandlerResult result = handler.handle(subConversationId, goal);
                if (!result.error()) {
                    return result;
                }
                lastResult = result;
                // P1-4: 4xx 业务失败重试无意义（再查也是没有），直接返回；仅 5xx 技术错误才重试
                if (result.code() >= 400 && result.code() < 500) {
                    LOGGER.info("子Agent[{}]业务失败(code={})，不重试直接返回: {}",
                            task.type(), result.code(), result.answer());
                    return result;
                }
                LOGGER.warn("子Agent[{}]第{}次执行返回错误(code={})，将重试: {}",
                        task.type(), attempt, result.code(), result.answer());
            } catch (Exception ex) {
                LOGGER.error("子Agent[{}]第{}次执行异常: {}", task.type(), attempt, ex.getMessage(), ex);
                lastResult = new HandlerResult("执行异常：" + ex.getMessage(), List.of(), HandlerResult.CODE_ERROR);
            }
        }
        LOGGER.warn("子Agent[{}]重试{}次后仍失败", task.type(), MAX_RETRY);
        return lastResult;
    }

    /**
     * 构建子任务goal：若声明了依赖，将依赖任务的执行结果拼入goal，供子Agent参考。
     */
    String buildGoalWithDeps(Task task, Map<String, HandlerResult> results, Map<String, Task> taskById) {
        String goal = task.goal();
        List<String> depIds = task.dependsOn() == null ? List.of() : task.dependsOn();
        if (depIds.isEmpty()) {
            return goal;
        }
        List<String> depBlocks = depIds.stream()
                .filter(results::containsKey)
                .map(depId -> {
                    Task depTask = taskById.get(depId);
                    String depGoal = depTask == null ? depId : depTask.goal();
                    return "【依赖任务：" + depGoal + "】\n" + results.get(depId).answer();
                })
                .toList();
        if (depBlocks.isEmpty()) {
            return goal;
        }
        return goal + "\n\n以下是依赖任务的执行结果，请基于这些结果完成任务：\n"
                + String.join("\n\n", depBlocks);
    }
}
