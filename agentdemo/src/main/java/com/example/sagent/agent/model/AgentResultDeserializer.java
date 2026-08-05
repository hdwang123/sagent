package com.example.sagent.agent.model;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.deser.std.StdDeserializer;

/**
 * AgentResult 自定义反序列化器
 * <p>
 * 容错处理 LLM 结构化输出中 {@code code} 字段的异常形态（缺失、null、非数字），
 * 统一归一化为成功（200）。避免 Jackson 将 null 绑定到原始类型 {@code int} 时抛出
 * {@code Cannot map null into type int}，导致 GSkill/ASkill/MCP 技能整体执行失败。
 * <p>
 * 语义与 {@link AgentResultParser} 的"非结构化按成功处理"惯例一致：
 * 技能实际执行结果以 {@code content} 为准，{@code code} 仅为业务状态标记。
 */
public class AgentResultDeserializer extends StdDeserializer<AgentResult> {

    /**
     * 无参构造，供 {@link tools.jackson.databind.annotation.JsonDeserialize} 反射实例化。
     */
    public AgentResultDeserializer() {
        super(AgentResult.class);
    }

    /**
     * 反序列化：code 缺失/null/非法时归一化为成功（200），content 原样提取。
     *
     * @param p    解析器
     * @param ctxt 反序列化上下文
     * @return 反序列化后的 AgentResult
     * @throws JacksonException JSON 解析异常
     */
    @Override
    public AgentResult deserialize(JsonParser p, DeserializationContext ctxt) throws JacksonException {
        JsonNode node = p.readValueAsTree();
        int code = AgentResult.CODE_SUCCESS;
        JsonNode codeNode = node.get("code");
        if (codeNode != null && !codeNode.isNull()) {
            if (codeNode.isNumber()) {
                code = codeNode.asInt();
            } else if (codeNode.isTextual()) {
                try {
                    code = Integer.parseInt(codeNode.asText());
                } catch (NumberFormatException e) {
                    // 非法文本视为未提供 code，按成功处理
                    code = AgentResult.CODE_SUCCESS;
                }
            }
        }
        JsonNode contentNode = node.get("content");
        String content = (contentNode == null || contentNode.isNull()) ? null : contentNode.asText();
        return new AgentResult(code, content);
    }
}
