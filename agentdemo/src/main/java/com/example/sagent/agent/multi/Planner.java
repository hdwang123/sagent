package com.example.sagent.agent.multi;

import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.cost.CostMonitorService;
import com.example.sagent.agent.cost.TokenUsageCostAdvisor;
import com.example.sagent.agent.model.Task;
import com.example.sagent.agent.model.TaskPlan;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.SimpleLoggerAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 多Agent任务规划器
 * <p>
 * 职责：LLM结构化输出任务计划 + 图校验（id去重/悬空依赖过滤/环检测）+ 重新规划。
 * 接入多轮记忆：读取主会话历史拼入prompt，让Planner理解"刚才那个"等指代（P3-12）。
 */
@Component
public class Planner {

    private static final Logger LOGGER = LoggerFactory.getLogger(Planner.class);

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
    private final ChatMemory multiAgentChatMemory;
    private final ConversationHistory conversationHistory;
    private final CostMonitorService costMonitorService;

    public Planner(ChatClient.Builder chatClientBuilder,
                   @Qualifier("multiAgentChatMemory") ChatMemory multiAgentChatMemory,
                   ConversationHistory conversationHistory,
                   CostMonitorService costMonitorService) {
        this.plannerClient = chatClientBuilder.build();
        this.multiAgentChatMemory = multiAgentChatMemory;
        this.conversationHistory = conversationHistory;
        this.costMonitorService = costMonitorService;
    }

    /**
     * Planner：LLM结构化输出任务计划。
     * P3-12: 读取主会话历史拼入prompt，让Planner理解多轮上下文（如"刚才那个产品"的指代）。
     * P1-6: 拿到结果后做图校验（id去重/悬空依赖过滤/环检测），非法时降级处理，避免送进execute白跑。
     *
     * @param conversationId 会话ID，用于读取多轮历史
     * @param message        当前用户消息
     * @return 校验后的任务计划
     */
    public TaskPlan plan(String conversationId, String message) {
        // P3-12: 拼入多Agent独立会话历史，让Planner理解多轮上下文（与单Agent的chatMemory隔离）
        String history = conversationHistory.format(multiAgentChatMemory.get(conversationId));
        String userInput = (history == null || history.isBlank())
                ? message
                : "【会话历史】\n" + history + "\n\n【当前请求】\n" + message;

        var callResponse = plannerClient.prompt()
                .system(PLANNER_PROMPT)
                .user(userInput)
                .advisors(new SimpleLoggerAdvisor())
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, conversationId)
                        .param("operationType", "multi/planner"))
                .advisors(new TokenUsageCostAdvisor(costMonitorService))
                .call();

        TaskPlan plan = callResponse.entity(TaskPlan.class, spec -> spec.validateSchema());
        if (plan == null || plan.tasks() == null || plan.tasks().isEmpty()) {
            LOGGER.warn("Planner返回空计划，降级为单个聊天任务: {}", message);
            return new TaskPlan(List.of(new Task("t1", AgentType.CHAT, message, List.of())));
        }

        // P1-6: 图校验（id去重/悬空依赖过滤/环检测）
        TaskPlan validated = validateAndFixPlan(plan);
        logPlan("Planner拆解", validated.tasks());
        return validated;
    }

    /**
     * 方案B：失败触发重新规划。基于已完成结果、失败原因和剩余任务，让Planner重新规划剩余任务。
     */
    public List<Task> replan(String message, Map<String, HandlerResult> results,
                              Set<String> failedIds, List<Task> pending, TaskPlan plan) {
        String doneTasks = results.entrySet().stream()
                .filter(e -> !failedIds.contains(e.getKey()))
                .map(e -> {
                    Task t = plan.taskById().get(e.getKey());
                    String label = t == null ? e.getKey() : t.goal();
                    return "id=" + e.getKey() + ", goal=" + label + ", 结果=" + e.getValue().answer();
                })
                .collect(Collectors.joining("\n"));
        String failedTasks = failedIds.stream()
                .map(id -> {
                    Task t = plan.taskById().get(id);
                    String label = t == null ? id : t.goal();
                    return "id=" + id + ", goal=" + label + ", 失败原因=" + results.get(id).answer();
                })
                .collect(Collectors.joining("\n"));
        String pendingTasks = pending.stream()
                .map(t -> "id=" + t.id() + ", type=" + t.type() + ", goal=" + t.goal()
                        + ", dependsOn=" + t.dependsOn())
                .collect(Collectors.joining("\n"));

        var callResponse = plannerClient.prompt()
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
                .advisors(advisor -> advisor
                        .param(ChatMemory.CONVERSATION_ID, "replan")
                        .param("operationType", "multi/replan"))
                .advisors(new TokenUsageCostAdvisor(costMonitorService))
                .call();

        TaskPlan newPlan = callResponse.entity(TaskPlan.class, spec -> spec.validateSchema());
        if (newPlan == null || newPlan.tasks() == null || newPlan.tasks().isEmpty()) {
            LOGGER.warn("重新规划返回空，剩余任务不再执行");
            return List.of();
        }

        // P1-6: 重新规划结果同样需要图校验
        TaskPlan validated = validateAndFixPlan(newPlan);
        logPlan("重新规划", validated.tasks());
        return validated.tasks();
    }

    /**
     * P1-6: 图校验——id去重、悬空依赖过滤、循环依赖检测。
     * 非法时尽量容错修复（去重/过滤悬空依赖），循环依赖无法修复时降级为单聊天任务。
     *
     * @param plan LLM输出的原始计划
     * @return 校验/修复后的计划
     */
    TaskPlan validateAndFixPlan(TaskPlan plan) {
        List<Task> tasks = plan.tasks();
        // 1. id 去重：保留首次出现的，重复的跳过
        Set<String> seen = new HashSet<>();
        List<Task> deduped = new ArrayList<>();
        for (Task t : tasks) {
            if (seen.add(t.id())) {
                deduped.add(t);
            } else {
                LOGGER.warn("Planner输出重复id[{}]，已跳过该任务", t.id());
            }
        }
        // 2. 收集所有合法 id
        Set<String> validIds = deduped.stream().map(Task::id).collect(Collectors.toSet());
        // 3. 过滤悬空依赖（dependsOn 引用了不存在的 id）
        List<Task> cleaned = new ArrayList<>();
        for (Task t : deduped) {
            List<String> deps = t.dependsOn() == null ? List.of() : t.dependsOn();
            List<String> validDeps = deps.stream().filter(validIds::contains).toList();
            if (validDeps.size() < deps.size()) {
                List<String> dangling = deps.stream().filter(d -> !validIds.contains(d)).toList();
                LOGGER.warn("任务[{}]存在悬空依赖，已过滤: {}", t.id(), dangling);
            }
            cleaned.add(new Task(t.id(), t.type(), t.goal(), validDeps));
        }
        // 4. 循环依赖检测：Kahn 拓扑排序，无法完成拓扑说明有环
        if (hasCycle(cleaned)) {
            LOGGER.warn("Planner输出存在循环依赖，降级为单个聊天任务");
            String fallbackGoal = cleaned.isEmpty() ? "" : cleaned.get(0).goal();
            return new TaskPlan(List.of(new Task("t1", AgentType.CHAT, fallbackGoal, List.of())));
        }
        return new TaskPlan(cleaned);
    }

    /**
     * 环检测：Kahn 算法（拓扑排序）。若拓扑排序处理不完所有节点，说明存在环。
     */
    boolean hasCycle(List<Task> tasks) {
        if (tasks.isEmpty()) {
            return false;
        }
        Map<String, Task> byId = tasks.stream()
                .collect(Collectors.toMap(Task::id, t -> t, (a, b) -> a));
        // 入度表：每个任务依赖了多少个其他任务
        Map<String, Integer> inDegree = new HashMap<>();
        for (Task t : tasks) {
            int deg = t.dependsOn() == null ? 0
                    : (int) t.dependsOn().stream().filter(byId::containsKey).count();
            inDegree.put(t.id(), deg);
        }
        // 邻接表：被依赖任务 -> 依赖它的任务列表
        Map<String, List<String>> adj = new HashMap<>();
        for (Task t : tasks) {
            if (t.dependsOn() != null) {
                for (String dep : t.dependsOn()) {
                    if (byId.containsKey(dep)) {
                        adj.computeIfAbsent(dep, k -> new ArrayList<>()).add(t.id());
                    }
                }
            }
        }
        // Kahn 拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) {
                queue.add(e.getKey());
            }
        }
        int processed = 0;
        while (!queue.isEmpty()) {
            String id = queue.poll();
            processed++;
            for (String next : adj.getOrDefault(id, List.of())) {
                int d = inDegree.get(next) - 1;
                inDegree.put(next, d);
                if (d == 0) {
                    queue.add(next);
                }
            }
        }
        return processed != tasks.size();
    }

    /**
     * 输出任务计划日志：每个子任务的 id/type/goal/dependsOn，便于调试观察 Planner 输出。
     */
    private void logPlan(String title, List<Task> tasks) {
        LOGGER.info("{}: {} 个子任务", title, tasks.size());
        for (Task t : tasks) {
            LOGGER.info("  [{}] type={}, goal={}, dependsOn={}", t.id(), t.type(), t.goal(), t.dependsOn());
        }
    }
}
