package com.example.sagent.agent.model;

import java.util.List;

/**
 * 任务计划
 * Planner输出的结构化结果，包含一组子任务
 */
public record TaskPlan(
        /**
         * 子任务列表
         */
        List<Task> tasks
) {
}
