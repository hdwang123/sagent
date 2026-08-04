package com.example.sagent.agent.model;

/**
 * 工具结构化返回对象
 * <p>
 * 用于 {@code @Tool(returnDirect = true)} 工具的标准化返回格式。
 * 工具方法返回该对象的 JSON 字符串（如 {@code {"code":200,"content":"..."}}），
 * 由 Handler 层通过 Jackson 反序列化后提取 {@link #code()} 与 {@link #content()}，
 * 保证 {@code returnDirect=true} 与结构化业务校验同时生效。
 * <p>
 * 约定：
 * <ul>
 *   <li>200：业务成功</li>
 *   <li>4xx：业务失败（如资源不存在、参数非法）</li>
 *   <li>5xx：技术错误（如 IO 异常、外部服务不可达）</li>
 * </ul>
 */
public record AgentResult(
        /** 业务状态码，200=成功，4xx=业务失败，5xx=技术错误 */
        int code,
        /** 回答正文（可包含下载链接、说明信息等） */
        String content
) {

    /** 默认成功状态码 */
    public static final int CODE_SUCCESS = 200;
    /** 资源不存在 */
    public static final int CODE_NOT_FOUND = 404;
    /** 业务执行失败 */
    public static final int CODE_BUSINESS_ERROR = 500;

    /**
     * 构造成功结果。
     *
     * @param content 回答正文
     * @return code=200 的 AgentResult
     */
    public static AgentResult success(String content) {
        return new AgentResult(CODE_SUCCESS, content);
    }

    /**
     * 构造失败结果，默认 500 业务错误码。
     *
     * @param content 失败原因/正文
     * @return code=500 的 AgentResult
     */
    public static AgentResult failure(String content) {
        return new AgentResult(CODE_BUSINESS_ERROR, content);
    }

    /**
     * 业务是否成功。
     *
     * @return code 在 [200, 300) 区间时返回 true
     */
    public boolean success() {
        return code >= 200 && code < 300;
    }
}
