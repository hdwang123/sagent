package com.example.sagent.agent.multi;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.core.HandlerRegistry;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.Task;
import com.example.sagent.agent.model.TaskPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.stereotype.Service;

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
 * 多Agent编排服务（演示版）
 * Planner拆解任务 -> Executor调度执行（复用现有Handler） -> 汇总Agent生成最终回答
 */
@Service
public class MultiAgentService {

    private static final Logger LOGGER = LoggerFactory.getLogger(MultiAgentService.class);

    private static final String PLANNER_PROMPT = """
            你是任务规划者。将用户的请求拆解为若干可独立完成的子任务，交给不同的子Agent并行或串行执行。

            可用的子Agent类型：
            【RAG】知识库问答：查询内部文档、项目说明、使用手册
            【GSKILL】数据查询：产品列表、价格、库存、数量统计、当前时间
            【SKILL】文件处理：生成Markdown文档（DocumentSkill）、读取已生成文档内容（DocumentSkill）、下载网页内容/图片/视频/音频/文档/截图（WebPageDownloadSkill）、压缩文件为ZIP
            【ASKILL】审批操作：删除产品、修改价格/库存（需人工审批）
            【MCP】外部服务：计算、天气、股票
            【CHAT】通用对话：闲聊、写作、翻译、总结

            拆解规则：
            1. 只拆解需要多个不同能力的复杂任务；简单任务只返回1个子任务
            2. 每个子任务必须有一个唯一标识 id（格式如 t1、t2、t3...，按顺序递增），用于被其他任务通过 dependsOn 引用
            3. 每个子任务的goal必须自包含、可独立执行，写明具体目标和参数（如"查询产品ABC的价格"）
            4. dependsOn是依赖的前序子任务 id 列表（数组）：如果子任务B需要子任务A的结果，B的dependsOn必须包含A的id；如果B需要多个子任务的结果（如"生成文档"同时依赖RAG查询和数据库查询），dependsOn就填这两个任务的id组成的数组；无依赖则dependsOn填空数组[]
            5. 最多拆解4个子任务
            6. type字段必须是RAG/GSKILL/SKILL/ASKILL/MCP/CHAT之一
            7. 示例一（单依赖）：用户要"查询产品并生成文档"，应拆为两个子任务：
               任务1: id=t1, type=GSKILL, goal=查询所有产品的信息, dependsOn=[]
               任务2: id=t2, type=SKILL, goal=将查询到的所有产品信息生成一份Markdown文档并保存, dependsOn=[t1]
            8. 示例二（多依赖合并）：用户要"结合知识库和产品数据生成一份报告"，应拆为三个子任务：
               任务1: id=t1, type=RAG, goal=查询Sagent项目介绍, dependsOn=[]
               任务2: id=t2, type=GSKILL, goal=查询所有产品的信息, dependsOn=[]
               任务3: id=t3, type=SKILL, goal=结合RAG查询到的项目介绍和数据库查询到的产品信息，生成一份综合报告Markdown文档, dependsOn=[t1, t2]
            """;

    private static final String AGGREGATE_PROMPT = """
            你是结果汇总助手。以下是多个子Agent分别完成的任务结果，请将它们整合成一份完整、连贯、有条理的回答给用户。
            不要重复结果中已有的信息，按逻辑顺序组织，使用中文回答。
            **必须保留子任务结果中的所有下载链接（/files/download/开头的URL），原样放在回答末尾的"下载链接"部分，不要省略或改写。**

            {subResults}
            """;

    private static final String REPLAN_PROMPT = """
            你是任务重新规划者。之前的任务计划在执行中部分失败，请基于当前进度重新规划"剩余未完成的任务"。

            重新规划要求：
            1. 只输出"接下来还需要做的任务"，不要重复已完成的任务
            2. 新任务 id 用 r1、r2...（避免和已有 id 冲突）
            3. dependsOn 可引用已成功完成任务的 id（如 t1）或新任务 id（如 r1）；不要引用失败任务的 id
            4. 如果某个失败任务可以换种方式重做，规划一个新任务来实现
            5. 如果失败任务无法绕过，可规划替代路径或放弃该方向
            6. type 必须是 RAG/GSKILL/SKILL/ASKILL/MCP/CHAT 之一
            7. 最多 4 个子任务
            """;

    private final ChatClient plannerClient;
    private final ChatClient aggregateClient;
    private final HandlerRegistry handlerRegistry;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * 单个子Agent执行超时阈值（秒），超时后整轮编排不再等待该任务，直接返回错误结果
     */
    private static final long SUB_AGENT_TIMEOUT_SECONDS = 60L;

    /**
     * 单个子任务失败后的重试次数（不含首次执行），重试时把上次失败原因拼入goal提示模型换种方式
     */
    private static final int MAX_RETRY = 1;

    /**
     * 失败后允许重新规划的最大次数，用完后改为依赖止损（跳过受影响的后续任务）
     */
    private static final int MAX_REPLAN = 2;

    public MultiAgentService(
            ChatClient.Builder chatClientBuilder,
            HandlerRegistry handlerRegistry
    ) {
        this.plannerClient = chatClientBuilder.build();
        this.aggregateClient = chatClientBuilder.build();
        this.handlerRegistry = handlerRegistry;
    }

    /**
     * 多Agent处理入口：Planner拆解 -> Executor执行 -> 汇总
     *
     * @param conversationId 会话ID
     * @param message        用户消息
     * @return 最终回答
     */
    public HandlerResult handle(String conversationId, String message) {
        // 1. Planner拆解任务（计划详情由 plan() 内部输出日志）
        TaskPlan plan = plan(message);

        // id -> Task 映射，供 execute/aggregate 按id查goal描述
        Map<String, Task> taskById = plan.tasks().stream()
                .collect(Collectors.toMap(Task::id, t -> t, (a, b) -> a, LinkedHashMap::new));

        // 2. Executor按依赖执行（结果以子任务id为键），传入原始消息供失败时重新规划
        Map<String, HandlerResult> results = execute(conversationId, plan.tasks(), taskById, message);

        // 3. 汇总Agent生成最终回答
        String answer = aggregate(message, results, taskById);
        // 4. 兜底：从子任务结果中提取下载链接，确保汇总遗漏时用户仍能下载
        List<String> downloadLinks = extractDownloadLinks(results);
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
        return new HandlerResult(answer, sources);
    }

    /**
     * Planner：LLM结构化输出任务计划
     */
    private TaskPlan plan(String message) {
        TaskPlan plan = plannerClient.prompt()
                .system(PLANNER_PROMPT)
                .user(message)
                .advisors(new SimpleLoggerAdvisor())
                .call()
                .entity(TaskPlan.class, spec -> spec.validateSchema());
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
            // 兜底：当作单个普通聊天任务
            LOGGER.warn("Planner返回空计划，降级为单个聊天任务: {}", message);
            return new TaskPlan(List.of(new Task("t1", AgentType.CHAT, message, List.of())));
        }
        // id 与 dependsOn 必须由 LLM 协同生成才能匹配，代码侧补 id 与 LLM 在 dependsOn 里的引用对不上，故不做兜底
        logPlan("Planner拆解", plan.tasks());
        return plan;
    }

    /**
     * 输出任务计划日志：每个子任务的 id/type/goal/dependsOn，便于调试观察 Planner 输出。
     *
     * @param title 日志标题（如 Planner拆解、重新规划）
     * @param tasks 子任务列表
     */
    private void logPlan(String title, List<Task> tasks) {
        LOGGER.info("{}: {} 个子任务", title, tasks.size());
        for (Task t : tasks) {
            LOGGER.info("  [{}] type={}, goal={}, dependsOn={}", t.id(), t.type(), t.goal(), t.dependsOn());
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
     */
    private Map<String, HandlerResult> execute(String conversationId, List<Task> tasks,
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
                // 不盲目执行（违背依赖语义），改为标记失败并退出，明确暴露LLM输出的依赖问题。
                LOGGER.warn("依赖无法满足（循环依赖或依赖id不存在），剩余任务标记失败跳过: {}",
                        pending.stream().map(t -> t.id() + ":" + t.goal()).toList());
                markFailed(results, taskById,
                        pending.stream().map(Task::id).collect(Collectors.toSet()),
                        "因依赖无法满足而跳过（循环依赖或依赖id不存在）");
                break;
            }
            LOGGER.info("波次{}就绪任务: {}", wave, ready.stream().map(Task::id).toList());
            pending.removeAll(ready);

            // 本轮并行执行：每个子任务加超时与异常兜底，避免单个任务卡死/抛错中断整轮
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
                        // 方案B：失败触发重新规划，让Planner基于已完成结果和失败原因调整剩余计划
                        replanCount++;
                        LOGGER.info("检测到{}个失败任务，触发第{}/{}次重新规划",
                                failedIds.size(), replanCount, MAX_REPLAN);
                        List<Task> newTasks = replan(message, results, failedIds, pending, taskById);
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
                    markFailed(results, taskById, toFail,
                            "因依赖任务失败而止损跳过");
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
     * 供 readyEmpty（循环依赖/依赖断裂全量止损）和 方案E（依赖失败链递归止损）共用，
     * 消除两处重复的"构造error结果 + put"代码。
     * 只负责标记失败，不从pending移除——readyEmpty 调用后直接break无需移除，
     * 方案E 调用后由调用方自行removeIf以继续循环。
     *
     * @param results  子任务结果（写入失败结果，code=500）
     * @param taskById id -> Task 映射，用于查goal作为可读标签
     * @param ids      需标记失败的任务id集合
     * @param reason   失败原因（拼入结果文本）
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
     * 超时或异常时降级为错误结果（{@link HandlerResult#error()}=true），不向上抛，
     * 保证整轮编排不被单个任务卡死或拖垮。
     *
     * @param conversationId 主会话ID
     * @param task           待执行子任务
     * @param results        已完成子任务结果（key为子任务id），用于依赖注入
     * @param taskById       id -> Task 映射，用于按依赖id查goal描述
     * @return 以子任务id为key、执行结果为value的异步Entry
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
     * 若任务声明了依赖，将所有依赖任务的执行结果一并拼入goal，供子Agent参考。
     * 整个方法体被 try-catch 包裹：任何异常都降级为错误结果返回，不向上抛出，
     * 避免单个子任务拖垮整轮编排（与 scheduleTask 中的超时兜底形成双重防护）。
     *
     * @param conversationId 主会话ID（仅用于降级聊天场景）
     * @param task           待执行的子任务
     * @param results        已完成子任务的结果（key为子任务id）
     * @param taskById       id -> Task 映射，用于按依赖id查goal作为描述文本
     */
    private HandlerResult runSubAgent(String conversationId, Task task,
                                      Map<String, HandlerResult> results, Map<String, Task> taskById) {
        String baseGoal = buildGoalWithDeps(task, results, taskById);
        HandlerResult lastResult = null;
        int maxAttempts = 1 + MAX_RETRY;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String goal = baseGoal;
            if (attempt > 1 && lastResult != null) {
                // 方案A：重试时把上次失败原因拼入goal，提示模型换种方式
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
                LOGGER.warn("子Agent[{}]第{}次执行返回错误，将重试: {}", task.type(), attempt, result.answer());
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
     *
     * @param task     待执行子任务
     * @param results  已完成子任务结果
     * @param taskById id -> Task 映射，用于按依赖id查goal作为可读描述
     * @return 拼接好依赖结果的goal文本
     */
    private String buildGoalWithDeps(Task task, Map<String, HandlerResult> results, Map<String, Task> taskById) {
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

    /**
     * 方案B：失败触发重新规划。基于已完成结果、失败原因和剩余任务，让Planner重新规划剩余任务。
     *
     * @param message   原始用户请求
     * @param results   已完成子任务结果（含成功的和失败的）
     * @param failedIds 失败任务id集合
     * @param pending   剩余未执行任务
     * @param taskById  id -> Task 映射（用于查goal描述）
     * @return 重新规划后的剩余任务列表
     */
    private List<Task> replan(String message, Map<String, HandlerResult> results,
                              Set<String> failedIds, List<Task> pending, Map<String, Task> taskById) {
        String doneTasks = results.entrySet().stream()
                .filter(e -> !failedIds.contains(e.getKey()))
                .map(e -> {
                    Task t = taskById.get(e.getKey());
                    String label = t == null ? e.getKey() : t.goal();
                    return "id=" + e.getKey() + ", goal=" + label + ", 结果=" + e.getValue().answer();
                })
                .collect(Collectors.joining("\n"));
        String failedTasks = failedIds.stream()
                .map(id -> {
                    Task t = taskById.get(id);
                    String label = t == null ? id : t.goal();
                    return "id=" + id + ", goal=" + label + ", 失败原因=" + results.get(id).answer();
                })
                .collect(Collectors.joining("\n"));
        String pendingTasks = pending.stream()
                .map(t -> "id=" + t.id() + ", type=" + t.type() + ", goal=" + t.goal()
                        + ", dependsOn=" + t.dependsOn())
                .collect(Collectors.joining("\n"));

        TaskPlan newPlan = plannerClient.prompt()
                .system(REPLAN_PROMPT)
                .user(u -> u.text("""
                        【原始用户请求】
                        {message}

                        【已成功完成的任务】
                        {doneTasks}

                        【失败的任务及原因】
                        {failedTasks}

                        【剩余未执行任务（原计划）】
                        {pendingTasks}
                        """)
                        .param("message", message)
                        .param("doneTasks", doneTasks.isBlank() ? "无" : doneTasks)
                        .param("failedTasks", failedTasks)
                        .param("pendingTasks", pendingTasks.isBlank() ? "无" : pendingTasks))
                .advisors(new SimpleLoggerAdvisor())
                .call()
                .entity(TaskPlan.class, spec -> spec.validateSchema());
        if (newPlan == null || newPlan.tasks() == null || newPlan.tasks().isEmpty()) {
            LOGGER.warn("重新规划返回空，剩余任务不再执行");
            return List.of();
        }
        logPlan("重新规划", newPlan.tasks());
        return newPlan.tasks();
    }

    /**
     * 汇总Agent：将子任务结果整合为最终回答。
     * 展示时用子任务goal作为可读标签（而非id），便于汇总模型理解。
     */
    private String aggregate(String message, Map<String, HandlerResult> results, Map<String, Task> taskById) {
        String subResults = results.entrySet().stream()
                .map(e -> {
                    Task t = taskById.get(e.getKey());
                    String label = t == null ? e.getKey() : t.goal();
                    return "任务: " + label + "\n结果: " + e.getValue().answer();
                })
                .collect(Collectors.joining("\n\n---\n\n"));
        return aggregateClient.prompt()
                .system(AGGREGATE_PROMPT)
                .user(user -> user.text("用户原始请求：{message}\n\n子任务结果：\n{subResults}")
                        .param("message", message)
                        .param("subResults", subResults))
                .call()
                .content();
    }

    /**
     * 从子任务结果中提取所有下载链接，用于兜底附加到汇总回答末尾
     *
     * @param results 子任务结果
     * @return 去重后的下载链接列表
     */
    private List<String> extractDownloadLinks(Map<String, HandlerResult> results) {
        return results.values().stream()
                .map(r -> r.answer() == null ? "" : r.answer())
                .flatMap(answer -> java.util.regex.Pattern
                        .compile("(/files/download/[^\\s，。！？、；：）)}\\]]+)")
                        .matcher(answer).results()
                        .map(m -> m.group(1)))
                .distinct()
                .toList();
    }
}
