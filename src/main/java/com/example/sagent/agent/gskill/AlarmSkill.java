package com.example.sagent.agent.gskill;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 通用skill示例（由大模型决定调用工具计划，自由组合调用各种工具）
 * @author wanghuidong
 * 时间： 2026/7/22 10:59
 */
@Component
public class AlarmSkill implements GSkill {
    private static final String DESCRIPTION = "获取时间，设置闹钟";

    @Override
    public String getName() {
        return "alarm";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }


    @Tool(description = "以用户所在时区获取当前时间")
    String getCurrentDateTime() {
        String time = LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
        System.out.println("获取时间：" + time);
        return time;
    }

    @Tool(description = "按照提供的时间给用户设置闹钟,时间格式ISO-8601")
    void setAlarm(String time) {
        LocalDateTime alarmTime = LocalDateTime.parse(time, DateTimeFormatter.ISO_DATE_TIME);
        System.out.println("闹钟已设置，时间：" + alarmTime);
    }

}
