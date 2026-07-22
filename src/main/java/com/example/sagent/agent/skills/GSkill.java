package com.example.sagent.agent.skills;

/**
 * 通用技能接口
 * 定义通用技能必须实现的方法
 */
public interface GSkill {

    /**
     * 获取技能名称
     *
     * @return 技能名称
     */
    String getName();

    /**
     * 获取技能描述
     *
     * @return 技能描述
     */
    String getDescription();
}