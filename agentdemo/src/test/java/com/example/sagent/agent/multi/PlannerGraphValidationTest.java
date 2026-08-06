package com.example.sagent.agent.multi;

import com.example.sagent.agent.cost.CostMonitorService;
import com.example.sagent.agent.memory.ConversationHistory;
import com.example.sagent.agent.model.AgentType;
import com.example.sagent.agent.model.Task;
import com.example.sagent.agent.model.TaskPlan;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Planner 图校验逻辑单元测试
 * <p>
 * 覆盖 hasCycle（Kahn 拓扑排序环检测）和 validateAndFixPlan（id 去重 / 悬空依赖过滤 / 环降级）。
 * 这两个方法为纯逻辑，不访问 Planner 的实例字段，通过 mock 构造 Planner 后直接调用。
 */
class PlannerGraphValidationTest {

    private Planner planner;

    @BeforeEach
    void setUp() {
        ChatClient.Builder mockBuilder = mock(ChatClient.Builder.class);
        when(mockBuilder.build()).thenReturn(mock(ChatClient.class));
        ConversationHistory mockHistory = mock(ConversationHistory.class);
        planner = new Planner(mockBuilder, mock(ChatMemory.class), mockHistory, mock(CostMonitorService.class));
    }

    // === hasCycle ===

    @Test
    void hasCycle_empty_returnsFalse() {
        assertThat(planner.hasCycle(List.of())).isFalse();
    }

    @Test
    void hasCycle_noDeps_returnsFalse() {
        List<Task> tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of()));
        assertThat(planner.hasCycle(tasks)).isFalse();
    }

    @Test
    void hasCycle_linearDeps_returnsFalse() {
        List<Task> tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of("t1")),
                task("t3", List.of("t2")));
        assertThat(planner.hasCycle(tasks)).isFalse();
    }

    @Test
    void hasCycle_twoNodeCycle_returnsTrue() {
        List<Task> tasks = List.of(
                task("t1", List.of("t2")),
                task("t2", List.of("t1")));
        assertThat(planner.hasCycle(tasks)).isTrue();
    }

    @Test
    void hasCycle_selfLoop_returnsTrue() {
        List<Task> tasks = List.of(
                task("t1", List.of("t1")));
        assertThat(planner.hasCycle(tasks)).isTrue();
    }

    @Test
    void hasCycle_diamondNoCycle_returnsFalse() {
        // 菱形：t1 ← t2, t1 ← t3, t2 ← t4, t3 ← t4
        List<Task> tasks = List.of(
                task("t1", List.of()),
                task("t2", List.of("t1")),
                task("t3", List.of("t1")),
                task("t4", List.of("t2", "t3")));
        assertThat(planner.hasCycle(tasks)).isFalse();
    }

    @Test
    void hasCycle_threeNodeCycle_returnsTrue() {
        // t1 ← t2 ← t3 ← t1
        List<Task> tasks = List.of(
                task("t1", List.of("t3")),
                task("t2", List.of("t1")),
                task("t3", List.of("t2")));
        assertThat(planner.hasCycle(tasks)).isTrue();
    }

    @Test
    void hasCycle_danglingDepIgnored_returnsFalse() {
        // t1 依赖不存在的 tX，Kahn 中被 byId::containsKey 过滤，不参与环检测
        List<Task> tasks = List.of(
                task("t1", List.of("tX")),
                task("t2", List.of("t1")));
        assertThat(planner.hasCycle(tasks)).isFalse();
    }

    // === validateAndFixPlan ===

    @Test
    void validate_normalPlan_unchanged() {
        TaskPlan plan = new TaskPlan(List.of(
                task("t1", List.of()),
                task("t2", List.of("t1"))));
        TaskPlan validated = planner.validateAndFixPlan(plan);
        assertThat(validated.tasks()).hasSize(2);
        assertThat(validated.tasks().get(0).id()).isEqualTo("t1");
        assertThat(validated.tasks().get(1).id()).isEqualTo("t2");
        assertThat(validated.tasks().get(1).dependsOn()).containsExactly("t1");
    }

    @Test
    void validate_duplicateId_deduped() {
        TaskPlan plan = new TaskPlan(List.of(
                task("t1", List.of()),
                task("t1", List.of()),
                task("t2", List.of("t1"))));
        TaskPlan validated = planner.validateAndFixPlan(plan);
        assertThat(validated.tasks()).hasSize(2);
        assertThat(validated.tasks().get(0).id()).isEqualTo("t1");
        assertThat(validated.tasks().get(1).id()).isEqualTo("t2");
    }

    @Test
    void validate_danglingDep_filtered() {
        TaskPlan plan = new TaskPlan(List.of(
                task("t1", List.of()),
                task("t2", List.of("t1", "tX"))));
        TaskPlan validated = planner.validateAndFixPlan(plan);
        assertThat(validated.tasks()).hasSize(2);
        assertThat(validated.tasks().get(1).dependsOn()).containsExactly("t1");
    }

    @Test
    void validate_allDanglingDeps_filteredToEmpty() {
        TaskPlan plan = new TaskPlan(List.of(
                task("t1", List.of("tA", "tB"))));
        TaskPlan validated = planner.validateAndFixPlan(plan);
        assertThat(validated.tasks()).hasSize(1);
        assertThat(validated.tasks().get(0).dependsOn()).isEmpty();
    }

    @Test
    void validate_cycle_downgradeToSingleChat() {
        TaskPlan plan = new TaskPlan(List.of(
                task("t1", List.of("t2")),
                task("t2", List.of("t1"))));
        TaskPlan validated = planner.validateAndFixPlan(plan);
        assertThat(validated.tasks()).hasSize(1);
        assertThat(validated.tasks().get(0).id()).isEqualTo("t1");
        assertThat(validated.tasks().get(0).type()).isEqualTo(AgentType.CHAT);
        assertThat(validated.tasks().get(0).dependsOn()).isEmpty();
    }

    @Test
    void validate_cyclePreservesFirstGoal() {
        TaskPlan plan = new TaskPlan(List.of(
                task("t1", List.of("t2")),
                task("t2", List.of("t1"))));
        TaskPlan validated = planner.validateAndFixPlan(plan);
        // 降级为单聊天任务时保留第一个任务的 goal
        assertThat(validated.tasks().get(0).goal()).isEqualTo("goal-t1");
    }

    // === remapConflictingIds ===

    @Test
    void remap_conflictingWithFailedId_renamedAndDepsUpdated() {
        // 重规划新任务 t2 与已失败任务 t2 冲突，另一任务 r3 依赖它 → t2 重命名为 r1 并同步依赖引用
        TaskPlan plan = new TaskPlan(List.of(
                task("t2", List.of()),
                task("r3", List.of("t2"))));
        TaskPlan remapped = planner.remapConflictingIds(plan, Set.of("t2"));

        assertThat(remapped.tasks()).hasSize(2);
        assertThat(remapped.tasks().get(0).id()).isEqualTo("r1");
        assertThat(remapped.tasks().get(0).goal()).isEqualTo("goal-t2");  // goal 保留
        assertThat(remapped.tasks().get(1).id()).isEqualTo("r3");
        assertThat(remapped.tasks().get(1).dependsOn()).containsExactly("r1");  // 依赖引用同步更新
    }

    @Test
    void remap_noConflict_returnsSamePlan() {
        // 新任务均不与失败任务 id 冲突 → 计划原样返回
        TaskPlan plan = new TaskPlan(List.of(
                task("r1", List.of()),
                task("r2", List.of("r1"))));
        TaskPlan remapped = planner.remapConflictingIds(plan, Set.of("t1"));

        assertThat(remapped.tasks()).isEqualTo(plan.tasks());
    }

    @Test
    void remap_multipleConflicts_assignsUnusedRN() {
        // 两个任务都与失败 id 冲突，且 r1 已被计划内任务占用 → t2→r2, t3→r3
        TaskPlan plan = new TaskPlan(List.of(
                task("t2", List.of()),
                task("t3", List.of("t2")),
                task("r1", List.of())));
        TaskPlan remapped = planner.remapConflictingIds(plan, Set.of("t2", "t3"));

        assertThat(remapped.tasks()).hasSize(3);
        assertThat(remapped.tasks().get(0).id()).isEqualTo("r2");
        assertThat(remapped.tasks().get(1).id()).isEqualTo("r3");
        assertThat(remapped.tasks().get(1).dependsOn()).containsExactly("r2");
        assertThat(remapped.tasks().get(2).id()).isEqualTo("r1");
    }

    private Task task(String id, List<String> dependsOn) {
        return new Task(id, AgentType.GSKILL, "goal-" + id, dependsOn);
    }
}
