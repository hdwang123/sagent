package com.example.sagent.agent.model;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 任务计划
 * <p>
 * Planner 输出的结构化结果，包含子任务列表及 id→Task 索引。
 * taskById 为可变索引：初始由 tasks 自动建立，Executor 在重新规划时对其增删，
 * 供 execute/aggregate 按 id 查 Task，避免在方法间单独传递 taskById。
 */
public record TaskPlan(
        /**
         * 子任务列表
         */
        List<Task> tasks,
        /**
         * id→Task 可变索引。@JsonIgnore 不参与 LLM 序列化，初始由 tasks 重建，
         * 之后 Executor 可增删以跟踪重新规划后的任务集。
         */
        @JsonIgnore Map<String, Task> taskById
) {

    public TaskPlan {
        tasks = tasks == null ? List.of() : List.copyOf(tasks);
        // taskById 始终从 tasks 重建为可变 LinkedHashMap（忽略外部传入，保证初始一致），
        // 之后 Executor 可对其增删以跟踪重新规划后的任务集
        Map<String, Task> index = new LinkedHashMap<>();
        for (Task t : tasks) {
            index.putIfAbsent(t.id(), t);
        }
        taskById = index;
    }

    /**
     * 便捷构造：仅传 tasks，自动建立 id→Task 索引。
     *
     * @param tasks 子任务列表
     */
    public TaskPlan(List<Task> tasks) {
        this(tasks, null);
    }
}
