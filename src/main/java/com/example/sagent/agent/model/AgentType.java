package com.example.sagent.agent.model;

/**
 * Agent类型枚举
 * 定义系统支持的消息处理类型
 */
public enum AgentType {

    /**
     * 普通聊天：闲聊、写作、翻译、总结、通用知识等
     */
    CHAT,

    /**
     * RAG检索：基于知识库的问答
     */
    RAG,

    /**
     * 数据库查询：查询产品数据等结构化业务数据
     */
    DATABASE,

    /**
     * SKILL技能：多步骤任务，涉及多个工具的组合使用
     */
    SKILL,

    /**
     * 通用技能：自由组合调用各种工具
     */
    GSKILL,

    /**
     * MCP：调用外部 MCP 服务器提供的工具
     */
    MCP
}