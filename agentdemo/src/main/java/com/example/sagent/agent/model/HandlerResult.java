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
        List<String> sources
) {

    /**
     * 构造函数，仅包含回答内容
     *
     * @param answer 回答内容
     */
    public HandlerResult(String answer) {
        this(answer, List.of());
    }
}