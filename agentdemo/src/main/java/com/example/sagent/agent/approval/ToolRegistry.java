package com.example.sagent.agent.approval;

import com.example.sagent.agent.skills.ASkill;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 工具注册表
 * 使用 Spring AI 原生 ToolCallback 机制注册所有 ASkill Bean 的 @Tool 方法
 * 供 ApprovalController 在审批通过后通过 ToolCallback.call() 重新唤起原始工具方法
 */
@Component
public class ToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);

    private final ToolCallbackResolver resolver;

    public ToolRegistry(List<ASkill> skills) {
        // 使用 Spring AI 原生 API 自动扫描 @Tool 注解并创建 ToolCallback
        ToolCallback[] callbacks = ToolCallbacks.from(skills.toArray());
        this.resolver = new StaticToolCallbackResolver(List.of(callbacks));
        LOGGER.info("ToolRegistry initialized with {} tools", callbacks.length);
    }

    /**
     * 根据工具名获取对应的 ToolCallback，用于审批通过后重新调用
     */
    public ToolCallback resolveTool(String toolName) {
        ToolCallback callback = resolver.resolve(toolName);
        if (callback == null) {
            throw new IllegalArgumentException("no tool registered with name: " + toolName);
        }
        return callback;
    }
}
