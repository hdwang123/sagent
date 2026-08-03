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
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    private final ChatClient plannerClient;
    private final ChatClient aggregateClient;
    private final HandlerRegistry handlerRegistry;
    private final ExecutorService executor = Executors.newFixedThreadPool(4);

    /**
     * 单个子Agent执行超时阈值（秒），超时后整轮编排不再等待该任务，直接返回错误结果
     */
    private static final long SUB_AGENT_TIMEOUT_SECONDS = 60L;

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
        // 1. Planner拆解任务
        TaskPlan plan = plan(message);
        LOGGER.info("Planner拆解出 {} 个子任务: {}", plan.tasks().size(),
                plan.tasks().stream().map(Task::goal).toList());

        // id -> Task 映射，供 execute/aggregate 按id查goal描述
        Map<String, Task> taskById = plan.tasks().stream()
                .collect(Collectors.toMap(Task::id, t -> t, (a, b) -> a, LinkedHashMap::new));

        // 2. Executor按依赖执行（结果以子任务id为键）
        Map<String, HandlerResult> results = execute(conversationId, plan.tasks(), taskById);

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
                .call()
                .entity(TaskPlan.class, spec -> spec.validateSchema());
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
            // 兜底：当作单个普通聊天任务
            return new TaskPlan(List.of(new Task("t1", AgentType.CHAT, message, List.of())));
        }
        // id 与 dependsOn 必须由 LLM 协同生成才能匹配，代码侧补 id 与 LLM 在 dependsOn 里的引用对不上，故不做兜底
        return plan;
    }

    /**
     * Executor：按依赖关系分波次执行子任务，无依赖的任务并行执行。
     * 结果以子任务 id 为键；单个子任务异常或超时不会中断整轮编排，会降级为错误结果。
     *
     * @param conversationId 会话ID
     * @param tasks          子任务列表
     * @param taskById       id -> Task 映射，供 runSubAgent 按依赖id查goal描述
     */
    private Map<String, HandlerResult> execute(String conversationId, List<Task> tasks, Map<String, Task> taskById) {
        Map<String, HandlerResult> results = new LinkedHashMap<>();
        List<Task> pending = new ArrayList<>(tasks);

        while (!pending.isEmpty()) {
            // 找出本轮可执行的任务（无依赖 或 所有依赖已完成）
            List<Task> ready = pending.stream()
                    .filter(t -> t.dependsOn() == null || t.dependsOn().isEmpty()
                            || t.dependsOn().stream().allMatch(results::containsKey))
                    .toList();
            if (ready.isEmpty()) {
                LOGGER.warn("任务依赖无法满足，剩余任务直接并行执行: {}",
                        pending.stream().map(Task::goal).toList());
                ready = List.copyOf(pending);
            }
            pending.removeAll(ready);

            // 本轮并行执行：每个子任务加超时与异常兜底，避免单个任务卡死/抛错中断整轮
            List<CompletableFuture<Map.Entry<String, HandlerResult>>> futures = ready.stream()
                    .map(task -> CompletableFuture.supplyAsync(
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
                                        "子任务[" + task.goal() + "]" + msg, List.of(), true));
                            }))
                    .toList();
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            futures.forEach(f -> {
                var entry = f.join();
                results.put(entry.getKey(), entry.getValue());
            });
        }
        return results;
    }

    /**
     * 执行单个子任务：复用现有Handler，使用独立会话ID避免污染主会话。
     * 若任务声明了依赖，将所有依赖任务的执行结果一并拼入goal，供子Agent参考。
     * 整个方法体被 try-catch 包裹：任何异常都降级为错误结果返回，不向上抛出，
     * 避免单个子任务拖垮整轮编排（与 execute 中的超时兜底形成双重防护）。
     *
     * @param conversationId 主会话ID（仅用于降级聊天场景）
     * @param task           待执行的子任务
     * @param results        已完成子任务的结果（key为子任务id）
     * @param taskById       id -> Task 映射，用于按依赖id查goal作为描述文本
     */
    private HandlerResult runSubAgent(String conversationId, Task task,
                                      Map<String, HandlerResult> results, Map<String, Task> taskById) {
        try {
            AgentHandler handler = handlerRegistry.get(task.type());
            if (handler == null) {
                LOGGER.warn("无处理器: {}, 子任务[{}]降级为普通聊天", task.type(), task.goal());
                return handlerRegistry.get(AgentType.CHAT).handle(conversationId, task.goal());
            }
            String subConversationId = UUID.randomUUID().toString();
            String goal = task.goal();
            List<String> depIds = task.dependsOn() == null ? List.of() : task.dependsOn();
            if (!depIds.isEmpty()) {
                List<String> depBlocks = depIds.stream()
                        .filter(results::containsKey)
                        .map(depId -> {
                            // 用依赖任务的goal作为可读描述，结果通过id查
                            Task depTask = taskById.get(depId);
                            String depGoal = depTask == null ? depId : depTask.goal();
                            return "【依赖任务：" + depGoal + "】\n" + results.get(depId).answer();
                        })
                        .toList();
                if (!depBlocks.isEmpty()) {
                    goal = task.goal() + "\n\n以下是依赖任务的执行结果，请基于这些结果完成任务：\n"
                            + String.join("\n\n", depBlocks);
                }
            }
            LOGGER.info("子Agent[{}, 会话{}] 执行: {}", task.type(), subConversationId, task.goal());
            return handler.handle(subConversationId, goal);
        } catch (Exception ex) {
            LOGGER.error("子Agent[{}]执行异常: {}", task.type(), ex.getMessage(), ex);
            return new HandlerResult("子任务[" + task.goal() + "]执行异常：" + ex.getMessage(), List.of(), true);
        }
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
