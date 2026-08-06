package com.example.sagent.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 任务计划
 * <p>
 * Planner 输出的结构化结果，包含子任务列表及 id→Task 的 O(1) 索引。
 * taskIndex 由 tasks 派生（LLM 只输出 tasks），查询统一走 {@link #taskById(String)}。
 * {@link #filterPending(Set)} 基于已完成任务集合过滤出待调度任务，生成新计划，
 * 供 replan 后动态替换 plan 引用，避免 tasks 与待调度集合的状态断层。
 */
public record TaskPlan(
        /**
         * 子任务列表
         */
        List<Task> tasks,
        /**
         * id→Task 的 O(1) 索引。@JsonIgnore 不参与 LLM 序列化/反序列化，
         * 始终由 tasks 派生，保证索引与列表一致。
         */
        @JsonIgnore Map<String, Task> taskIndex
) {

    public TaskPlan {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        // taskIndex 缺失（如 LLM 反序列化场景）时由 tasks 重建；显式传入时保留原样
        taskIndex = taskIndex == null ? buildIndex(tasks) : taskIndex;
    }

    /**
     * 便捷构造：仅传 tasks，自动建立 id→Task 索引。
     *
     * @param tasks 子任务列表
     */
    public TaskPlan(List<Task> tasks) {
        this(tasks, null);
    }

    /**
     * 构建 id→Task 索引；重复 id 时保留首次出现的任务
     */
    private static Map<String, Task> buildIndex(List<Task> tasks) {
        return tasks.stream().collect(Collectors.toMap(Task::id, Function.identity(), (a, b) -> a));
    }

    /**
     * 按 id 查询子任务（O(1)）
     *
     * @param id 子任务 id
     * @return 对应子任务；不存在时返回 null
     */
    public Task taskById(String id) {
        return taskIndex.get(id);
    }

    /**
     * 仅保留未完成的任务，生成新 Plan（用于 replan 后替换）
     *
     * @param completedIds 已完成（无需再调度）的任务 id 集合
     * @return 仅含待调度任务的新计划
     */
    public TaskPlan filterPending(Set<String> completedIds) {
        List<Task> pending = tasks.stream()
                .filter(t -> !completedIds.contains(t.id()))
                .toList();
        return new TaskPlan(pending);
    }
}
