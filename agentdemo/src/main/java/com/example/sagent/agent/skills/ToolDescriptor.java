package com.example.sagent.agent.skills;

/**
 * 技能描述信息接口
 * Skill/GSkill/ASkill 三个技能接口的公共父接口，
 * 提供统一的名称与描述，供消息分类器等场景动态构建工具清单
 */
public interface ToolDescriptor {

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
