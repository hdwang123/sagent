package com.example.sagent.agent.skills;

/**
 * 审批技能接口
 * 实现此接口的技能在工具被调用时会先创建审批记录，待人工审批通过后才能继续执行
 */
public interface ASkill extends ToolDescriptor {
}
