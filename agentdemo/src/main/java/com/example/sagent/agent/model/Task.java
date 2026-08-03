package com.example.sagent.agent.model;

import java.util.List;

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
         * 依赖的前序子任务指令列表，空列表表示无依赖（可并行）。
         * 支持多依赖：所有依赖任务完成后本任务才可执行，且依赖结果会一并注入goal供子Agent参考。
         */
        List<String> dependsOn
) {
}
