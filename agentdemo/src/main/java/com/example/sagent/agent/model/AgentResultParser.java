package com.example.sagent.agent.model;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;

/**
 * AgentResult 解析器
 * <p>
 * 用于 {@code @Tool(returnDirect = true)} 场景：工具返回值会被 Spring AI 透传为
 * {@code {"code":..., "content":...}} JSON 字符串，Handler 层（如 SkillHandler）
 * 通过本类反序列化提取 code 与 content，保证结构化业务校验生效。
 * <ul>
 *   <li>若返回值为形如 {@code {"code":..., "content":...}} 的 JSON 对象，
 *       则提取 code 与 content，业务状态可被编排层（如 MultiAgentService）使用；</li>
 *   <li>若为普通自然语言（LLM 未输出结构化结果），则按成功（code=200）处理。</li>
 * </ul>
 */
public final class AgentResultParser {

    private AgentResultParser() {
    }

    /**
     * 尝试将原始字符串解析为 {@link AgentResult}。
     * <p>
     * 仅当字符串为形如 {@code {"code":..., "content":...}} 的 JSON 对象且 content 非空时返回结果；
     * 其余情况（空白、自然语言、解析失败）返回 {@code null}，由调用方按自然语言/默认成功处理。
     *
     * @param objectMapper Jackson 序列化器
     * @param raw          LLM/工具返回的原始字符串
     * @return 解析成功的 AgentResult，否则返回 null
     */
    public static AgentResult tryParse(ObjectMapper objectMapper, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        // 仅在形如 JSON 对象时尝试反序列化，避免对普通自然语言回复误判
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return null;
        }
        try {
            AgentResult result = objectMapper.readValue(trimmed, AgentResult.class);
            return (result != null && result.content() != null) ? result : null;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 将原始字符串统一解析为 {@link HandlerResult}：
     * 结构化 {@code {code, content}} 提取 code/content；非结构化文本按成功（code=200）处理。
     * 各 Handler 的 handle() 统一通过此方法包装返回，保证返回结构一致。
     *
     * @param objectMapper Jackson 序列化器
     * @param raw          LLM/工具返回的原始字符串
     * @return 包装后的 HandlerResult
     */
    public static HandlerResult toHandlerResult(ObjectMapper objectMapper, String raw) {
        AgentResult result = tryParse(objectMapper, raw);
        if (result != null) {
            return new HandlerResult(result.content(), List.of(), result.code());
        }
        return new HandlerResult(raw == null ? "" : raw, List.of(), HandlerResult.CODE_SUCCESS);
    }
}
