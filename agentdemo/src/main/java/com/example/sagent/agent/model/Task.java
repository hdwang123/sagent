package com.example.sagent.agent.model;

import java.util.List;

/**
 * 多Agent子任务
 * 由Planner拆解生成，Executor执行
 *
 * @param id        子任务唯一标识（如 "t1"），用于被其他任务通过 dependsOn 引用
 * @param type      子任务目标类型（复用现有AgentType）
 * @param goal      子任务指令，将作为子Agent的输入消息
 * @param dependsOn 依赖的前序子任务 id 列表，空列表表示无依赖（可并行）。
 *                  支持多依赖：所有依赖任务完成后本任务才可执行，且依赖结果会一并注入goal供子Agent参考。
 */
public record Task(
        String id,
        AgentType type,
        String goal,
        List<String> dependsOn
) {
}
