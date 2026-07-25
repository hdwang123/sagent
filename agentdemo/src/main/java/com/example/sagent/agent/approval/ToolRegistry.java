package com.example.sagent.agent.approval;

import com.example.sagent.agent.skills.ASkill;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.aop.support.AopUtils;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 工具注册表
 * 扫描所有 ASkill Bean 的 @Tool 方法，建立 工具名 -> (bean, method) 映射
 * 供 ApprovalController 在审批通过后重新唤起原始工具方法
 */
@Component
public class ToolRegistry {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, ToolEntry> registry = new LinkedHashMap<>();

    public ToolRegistry(List<ASkill> skills) {
        for (ASkill skill : skills) {
            // 从原始目标类获取方法，确保参数名正确（CGLIB 代理方法不保留参数名信息）
            Class<?> clazz = AopUtils.isAopProxy(skill) ? AopUtils.getTargetClass(skill) : skill.getClass();
            for (Method method : clazz.getMethods()) {
                Tool tool = AnnotationUtils.findAnnotation(method, Tool.class);
                if (tool != null) {
                    String name = method.getName();
                    if (registry.containsKey(name)) {
                        LOGGER.warn("duplicate tool name: {} (will be overwritten)", name);
                    }
                    registry.put(name, new ToolEntry(skill, method));
                    LOGGER.debug("registered tool: {} -> {}.{}", name, skill.getClass().getSimpleName(), name);
                }
            }
        }
        LOGGER.info("ToolRegistry initialized with {} tools", registry.size());
    }

    /**
     * 根据工具名获取对应的 Bean 和方法，用于审批通过后重新调用
     */
    public ToolEntry getTool(String toolName) {
        ToolEntry entry = registry.get(toolName);
        if (entry == null) {
            throw new IllegalArgumentException("no tool registered with name: " + toolName);
        }
        return entry;
    }

    /**
     * 重新调用工具方法（审批通过后使用）
     */
    public String invokeTool(String toolName, Object... args) {
        ToolEntry entry = getTool(toolName);
        try {
            return (String) entry.method().invoke(entry.bean(), args);
        } catch (Exception e) {
            throw new RuntimeException("failed to invoke tool: " + toolName, e);
        }
    }

    public record ToolEntry(ASkill bean, Method method) {}
}
