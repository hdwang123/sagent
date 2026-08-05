package com.example.sagent.agent.audit;

/**
 * 操作类型枚举
 */
public enum OperationType {
    TOOL_CALL,          // 工具调用
    APPROVAL_SUBMIT,    // 审批提交
    APPROVAL_APPROVE,   // 审批通过
    PRODUCT_UPDATE,     // 产品修改（删除/价格/库存）
    PRODUCT_QUERY       // 产品查询
}
