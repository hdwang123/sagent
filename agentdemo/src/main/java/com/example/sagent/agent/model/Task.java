package com.example.sagent.agent.model;

/**
 * 多Agent子任务
 * 由Planner拆解生成，Executor执行
 */
public record Task(
        /**
         * 子任务目标类型（复用现有AgentType）
         */
        AgentType type,
        /**
         * 子任务指令，将作为子Agent的输入消息
         */
        String goal,
        /**
         * 依赖的前序子任务指令，空表示无依赖（可并行）
         */
        String dependsOn
) {
}
