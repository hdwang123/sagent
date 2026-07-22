package com.example.sagent.agent.base.model;

import java.util.List;

public record HandlerResult(String answer, List<String> sources) {

    public HandlerResult(String answer) {
        this(answer, List.of());
    }
}
