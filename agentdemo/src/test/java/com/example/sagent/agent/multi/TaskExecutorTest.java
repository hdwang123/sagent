package com.example.sagent.agent.multi;

import com.example.sagent.agent.core.AgentHandler;
import com.example.sagent.agent.core.HandlerRegistry;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.Task;
import com.example.sagent.agent.model.TaskPlan;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * TaskExecutor 单元测试
 * <p>
 * 覆盖核心调度逻辑：无依赖并行执行、依赖等待、readyEmpty 死锁兜底、
 * 4xx 不重试 / 5xx 重试、异常降级、buildGoalWithDeps 依赖注入。
 * 通过 mock HandlerRegistry 和 Planner 隔离 LLM 调用，只测调度/纠偏逻辑。
 */
class TaskExecutorTest {

    private HandlerRegistry handlerRegistry;
    private Planner planner;
    private TaskExecutor executor;

    @BeforeEach
    void setUp() {
        handlerRegistry = mock(HandlerRegistry.class);
        // mock Planner：大部分测试不触发 replan，构造函数需要非 null
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        when(mockBuilder.build()).thenReturn(mock(ChatClient.class));
        ConversationHistory mockHistory = mock(ConversationHistory.class);
        planner = mock(Planner.class);
        executor = new TaskExecutor(handlerRegistry, planner, 60, 1, 2, 4);
    }

    @AfterEach
    void tearDown() {
        // 关闭线程池，避免非守护线程阻止 JVM 退出
        executor.shutdown();
    }

    // === 正常执行 ===

    @Test
    void execute_twoIndependentTasks_bothSucceed() {
        Task t1 = new Task("t1", AgentType.CHAT, "任务1", List.of());
        Task t2 = new Task("t2", AgentType.CHAT, "任务2", List.of());

        AgentHandler chatHandler = mock(AgentHandler.class);
        when(handlerRegistry.get(AgentType.CHAT)).thenReturn(chatHandler);
        when(chatHandler.handle(anyString(), eq("任务1"))).thenReturn(new HandlerResult("结果1"));
        when(chatHandler.handle(anyString(), eq("任务2"))).thenReturn(new HandlerResult("结果2"));

        Map<String, HandlerResult> results = executor.execute("conv-1", new TaskPlan(List.of(t1, t2)), "测试消息");

        assertThat(results).hasSize(2);
        assertThat(results.get("t1").answer()).isEqualTo("结果1");
        assertThat(results.get("t2").answer()).isEqualTo("结果2");
        assertThat(results.get("t1").success()).isTrue();
        assertThat(results.get("t2").success()).isTrue();
    }

    @Test
    void execute_dependentTask_executesAfterDependency() {
        Task t1 = new Task("t1", AgentType.GSKILL, "查询数据", List.of());
        Task t2 = new Task("t2", AgentType.SKILL, "生成文档", List.of("t1"));

        AgentHandler gskillHandler = mock(AgentHandler.class);
        AgentHandler skillHandler = mock(AgentHandler.class);
        when(handlerRegistry.get(AgentType.GSKILL)).thenReturn(gskillHandler);
        when(handlerRegistry.get(AgentType.SKILL)).thenReturn(skillHandler);
        when(gskillHandler.handle(anyString(), eq("查询数据")))
                .thenReturn(new HandlerResult("产品A,产品B"));
        // t2 的 goal 被 buildGoalWithDeps 拼入依赖结果，仍包含"生成文档"
        when(skillHandler.handle(anyString(), contains("生成文档")))
                .thenReturn(new HandlerResult("文档已生成：/files/download/doc/test.md"));

        Map<String, HandlerResult> results = executor.execute("conv-1", new TaskPlan(List.of(t1, t2)), "测试消息");

        assertThat(results).hasSize(2);
        assertThat(results.get("t1").answer()).isEqualTo("产品A,产品B");
        assertThat(results.get("t2").answer()).contains("/files/download/");
    }

    // === 死锁兜底 ===

    @Test
    void execute_circularDependency_marksFailed() {
        // t1 依赖 t2, t2 依赖 t1 → readyEmpty → 标记失败
        Task t1 = new Task("t1", AgentType.CHAT, "任务1", List.of("t2"));
        Task t2 = new Task("t2", AgentType.CHAT, "任务2", List.of("t1"));

        Map<String, HandlerResult> results = executor.execute("conv-1", new TaskPlan(List.of(t1, t2)), "测试消息");

        assertThat(results).hasSize(2);
        assertThat(results.get("t1").error()).isTrue();
        assertThat(results.get("t2").error()).isTrue();
        // 不应调用任何 handler（没有就绪任务）
        verifyNoInteractions(handlerRegistry);
    }

    @Test
    void execute_danglingDep_marksFailed() {
        // t1 依赖不存在的 tX → readyEmpty → 标记失败
        Task t1 = new Task("t1", AgentType.CHAT, "任务1", List.of("tX"));

        Map<String, HandlerResult> results = executor.execute("conv-1", new TaskPlan(List.of(t1)), "测试消息");

        assertThat(results).hasSize(1);
        assertThat(results.get("t1").error()).isTrue();
        verifyNoInteractions(handlerRegistry);
    }

    // === 4xx / 5xx 重试策略 ===

    @Test
    void execute_4xxError_noRetry() {
        Task t1 = new Task("t1", AgentType.GSKILL, "查询不存在的产品", List.of());

        AgentHandler handler = mock(AgentHandler.class);
        when(handlerRegistry.get(AgentType.GSKILL)).thenReturn(handler);
        when(handler.handle(anyString(), anyString()))
                .thenReturn(new HandlerResult("产品不存在", List.of(), 404));

        Map<String, HandlerResult> results = executor.execute("conv-1", new TaskPlan(List.of(t1)), "测试消息");

        assertThat(results.get("t1").code()).isEqualTo(404);
        assertThat(results.get("t1").error()).isTrue();
        // 4xx 不重试：handle 只被调用 1 次
        verify(handler, times(1)).handle(anyString(), anyString());
    }

    @Test
    void execute_5xxError_retriesOnceAndSucceeds() {
        Task t1 = new Task("t1", AgentType.GSKILL, "查询数据", List.of());

        AgentHandler handler = mock(AgentHandler.class);
        when(handlerRegistry.get(AgentType.GSKILL)).thenReturn(handler);
        // 第一次 500 失败，第二次成功
        when(handler.handle(anyString(), anyString()))
                .thenReturn(new HandlerResult("技术错误", List.of(), 500))
                .thenReturn(new HandlerResult("查询成功"));

        Map<String, HandlerResult> results = executor.execute("conv-1", new TaskPlan(List.of(t1)), "测试消息");

        assertThat(results.get("t1").answer()).isEqualTo("查询成功");
        assertThat(results.get("t1").success()).isTrue();
        // 5xx 重试 1 次：handle 被调用 2 次
        verify(handler, times(2)).handle(anyString(), anyString());
    }

    // === 异常降级 ===

    @Test
    void execute_handlerThrowsException_degradesToError() {
        Task t1 = new Task("t1", AgentType.CHAT, "任务1", List.of());

        AgentHandler handler = mock(AgentHandler.class);
        when(handlerRegistry.get(AgentType.CHAT)).thenReturn(handler);
        when(handler.handle(anyString(), anyString()))
                .thenThrow(new RuntimeException("连接超时"));

        Map<String, HandlerResult> results = executor.execute("conv-1", new TaskPlan(List.of(t1)), "测试消息");

        assertThat(results.get("t1").error()).isTrue();
        assertThat(results.get("t1").answer()).contains("连接超时");
    }

    // === buildGoalWithDeps ===

    @Test
    void buildGoalWithDeps_noDeps_returnsOriginalGoal() {
        Task task = new Task("t1", AgentType.CHAT, "原始goal", List.of());
        String goal = executor.buildGoalWithDeps(task, new LinkedHashMap<>(), new TaskPlan(List.of(task)));
        assertThat(goal).isEqualTo("原始goal");
    }

    @Test
    void buildGoalWithDeps_withDeps_injectsDependencyResults() {
        Task depTask = new Task("t1", AgentType.GSKILL, "查询数据", List.of());
        Task task = new Task("t2", AgentType.SKILL, "生成文档", List.of("t1"));
        Map<String, HandlerResult> results = new LinkedHashMap<>();
        results.put("t1", new HandlerResult("产品A,产品B"));

        String goal = executor.buildGoalWithDeps(task, results, new TaskPlan(List.of(depTask, task)));

        assertThat(goal).contains("生成文档");
        assertThat(goal).contains("产品A,产品B");
        assertThat(goal).contains("依赖任务");
    }

    @Test
    void buildGoalWithDeps_depNotInResults_returnsOriginalGoal() {
        // 依赖任务尚未完成（不在 results 中），goal 不注入依赖结果
        Task depTask = new Task("t1", AgentType.GSKILL, "查询数据", List.of());
        Task task = new Task("t2", AgentType.SKILL, "生成文档", List.of("t1"));

        String goal = executor.buildGoalWithDeps(task, new LinkedHashMap<>(), new TaskPlan(List.of(depTask, task)));

        assertThat(goal).isEqualTo("生成文档");
    }

    // === replan 与已完成任务过滤 ===

    @Test
    void execute_replanContainingCompletedTask_filtersItOut() {
        // t1 成功、t2 失败(500)、t3 依赖 t1+t2 待调度 → 波次1后触发 replan
        Task t1 = new Task("t1", AgentType.CHAT, "任务1", List.of());
        Task t2 = new Task("t2", AgentType.CHAT, "任务2", List.of());
        Task t3 = new Task("t3", AgentType.CHAT, "任务3", List.of("t1", "t2"));

        AgentHandler chatHandler = mock(AgentHandler.class);
        when(handlerRegistry.get(AgentType.CHAT)).thenReturn(chatHandler);
        when(chatHandler.handle(anyString(), eq("任务1"))).thenReturn(new HandlerResult("结果1"));
        when(chatHandler.handle(anyString(), eq("任务2")))
                .thenReturn(new HandlerResult("技术错误", List.of(), 500));
        Task r1 = new Task("r1", AgentType.CHAT, "重做任务2", List.of());
        Task r3 = new Task("r3", AgentType.CHAT, "任务3", List.of("r1"));
        when(chatHandler.handle(anyString(), contains("重做任务2"))).thenReturn(new HandlerResult("重做成功"));
        when(chatHandler.handle(anyString(), contains("任务3"))).thenReturn(new HandlerResult("任务3完成"));
        // replan 返回的新计划错误地带上已成功完成的 t1，应被 filterPending 过滤
        when(planner.replan(anyString(), anyMap(), anySet(), anyList(), any()))
                .thenReturn(new TaskPlan(List.of(t1, r1, r3)));

        Map<String, HandlerResult> results =
                executor.execute("conv-1", new TaskPlan(List.of(t1, t2, t3)), "测试消息");

        assertThat(results.get("t1").success()).isTrue();
        assertThat(results.get("r1").answer()).isEqualTo("重做成功");
        assertThat(results.get("r3").answer()).isEqualTo("任务3完成");
        // 已完成任务 t1 未被重复执行（handle 仅被调用 1 次）
        verify(chatHandler, times(1)).handle(anyString(), eq("任务1"));
    }

    // === 辅助方法 ===

    /**
     * Mockito eq() 的 shortcut，避免额外 import
     */
    private static String eq(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
