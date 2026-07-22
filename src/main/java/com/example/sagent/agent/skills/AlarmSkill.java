package com.example.sagent.agent.skills;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 闹钟技能
 * 提供获取时间和设置闹钟功能
 */
@Component
public class AlarmSkill implements GSkill {

    /**
     * 技能描述
     */
    private static final String DESCRIPTION = "获取时间，设置闹钟";

    /**
     * 获取技能名称
     *
     * @return 技能名称
     */
    @Override
    public String getName() {
        return "alarm";
    }

    /**
     * 获取技能描述
     *
     * @return 技能描述
     */
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * 获取当前时间
     * 以用户所在时区返回当前时间
     *
     * @return 当前时间字符串
     */
    @Tool(description = "以用户所在时区获取当前时间")
    String getCurrentDateTime() {
        String time = LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
        System.out.println("获取时间：" + time);
        return time;
    }

    /**
     * 设置闹钟
     * 按照提供的时间给用户设置闹钟
     *
     * @param time 闹钟时间，格式ISO-8601
     */
    @Tool(description = "按照提供的时间给用户设置闹钟,时间格式ISO-8601")
    void setAlarm(String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("闹钟已设置，时间：" + alarmTime);
    }
}