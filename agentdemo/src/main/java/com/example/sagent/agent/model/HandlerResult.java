package com.example.sagent.agent.model;

import java.util.List;

/**
 * 处理器结果对象
 * 封装AgentHandler处理后的结果
 */
public record HandlerResult(
        /**
         * 回答内容
         */
        String answer,
        /**
         * 来源列表
         */
        List<String> sources,
        /**
         * 是否执行失败（如MCP连接失败等异常场景）。
         * 为true时answer通常是错误说明，前端可据此展示错误样式而非正常回答
         */
        boolean error
) {

    /**
     * 构造函数，仅包含回答内容
     *
     * @param answer 回答内容
     */
    public HandlerResult(String answer) {
        this(answer, List.of(), false);
    }

    /**
     * 构造函数，包含回答内容与来源
     *
     * @param answer  回答内容
     * @param sources 来源列表
     */
    public HandlerResult(String answer, List<String> sources) {
        this(answer, sources, false);
    }
}
