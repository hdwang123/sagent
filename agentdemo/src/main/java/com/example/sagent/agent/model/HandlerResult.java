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
         * 业务状态码，与 {@link AgentResult#code()} 语义一致：
         * 200=成功，4xx=业务失败（如资源不存在），5xx=技术错误（如 IO 异常、外部服务不可达）。
         * 替代旧版 boolean error 字段，前端/编排层据此判断是否成功并选择不同处理策略。
         */
        int code
) {

    /** 默认成功状态码 */
    public static final int CODE_SUCCESS = AgentResult.CODE_SUCCESS;
    /** 默认技术错误状态码 */
    public static final int CODE_ERROR = AgentResult.CODE_BUSINESS_ERROR;

    /**
     * 构造函数，仅包含回答内容（默认 code=200 成功）
     *
     * @param answer 回答内容
     */
    public HandlerResult(String answer) {
        this(answer, List.of(), CODE_SUCCESS);
    }

    /**
     * 构造函数，包含回答内容与来源（默认 code=200 成功）
     *
     * @param answer  回答内容
     * @param sources 来源列表
     */
    public HandlerResult(String answer, List<String> sources) {
        this(answer, sources, CODE_SUCCESS);
    }

    /**
     * 业务是否成功。
     *
     * @return code 在 [200, 300) 区间时返回 true
     */
    public boolean success() {
        return code >= 200 && code < 300;
    }

    /**
     * 是否执行失败，兼容旧版 boolean error 语义，便于外部调用方平滑迁移。
     *
     * @return 业务失败时返回 true
     */
    public boolean error() {
        return !success();
    }
}
