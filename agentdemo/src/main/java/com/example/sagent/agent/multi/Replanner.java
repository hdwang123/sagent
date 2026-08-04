package com.example.sagent.agent.multi;

import com.example.sagent.agent.model.HandlerResult;
import com.example.sagent.agent.model.Task;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 重新规划回调接口
 * <p>
 * 解耦 {@link TaskExecutor} 与 {@link Planner}：Executor 在检测到失败时通过此回调触发重新规划，
 * 便于独立测试 Executor（可 mock replanner），避免 Executor 直接依赖 Planner 形成耦合。
 */
@FunctionalInterface
public interface Replanner {

    /**
     * 基于已完成结果、失败原因和剩余任务，重新规划剩余任务。
     *
     * @param message   原始用户请求
     * @param results   已完成子任务结果（含成功的和失败的）
     * @param failedIds 失败任务id集合
     * @param pending   剩余未执行任务
     * @param taskById  id -> Task 映射（用于查goal描述）
     * @return 重新规划后的剩余任务列表
     */
    List<Task> replan(String message, Map<String, HandlerResult> results,
                      Set<String> failedIds, List<Task> pending, Map<String, Task> taskById);
}
