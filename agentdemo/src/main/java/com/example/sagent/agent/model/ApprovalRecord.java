package com.example.sagent.agent.model;

import java.time.LocalDateTime;

public record ApprovalRecord(
        String id,
        String userId,
        String toolName,
        String argsJson,
        String status,
        String result,
        String autoResponse,
        LocalDateTime createTime,
        LocalDateTime updateTime
) {

    public ApprovalRecord(String userId, String toolName, String argsJson) {
        this(null, userId, toolName, argsJson, "PENDING", null, null, LocalDateTime.now(), null);
    }
}
