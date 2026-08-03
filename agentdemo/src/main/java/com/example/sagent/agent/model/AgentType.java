package com.example.sagent.agent.model;

/**
 * Agent类型枚举
 * 定义系统支持的消息处理类型
 */
public enum AgentType {

    /** 普通聊天：闲聊、写作、翻译、总结、通用知识等 */
    CHAT,

    /** 审批技能：敏感操作需要先提交审批，人工审核通过后自动执行 */
    ASKILL,

    /** RAG检索：基于知识库的问答 */
    RAG,

    /** SKILL技能：组合技能工具（如文档生成、网页下载），提示词引导单次调用单一工具 */
    SKILL,

    /** 通用技能：自由组合调用各种工具 */
    GSKILL,

    /** MCP：调用外部 MCP 服务器提供的工具 */
    MCP
}