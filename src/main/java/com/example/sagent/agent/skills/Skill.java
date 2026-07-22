package com.example.sagent.agent.skills;

/**
 * 技能接口
 * 定义企业固定技能必须实现的方法
 */
public interface Skill {

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