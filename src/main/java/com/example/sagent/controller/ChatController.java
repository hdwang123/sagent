package com.example.sagent.controller;

import com.example.sagent.agent.core.AgentService;
import com.example.sagent.agent.model.AgentResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final AgentService agentService;

    public ChatController(AgentService agentService) {
        this.agentService = agentService;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody ChatRequest request) {
        return agentService.ask(requireMessage(request.message()));
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "message must not be blank");
        }
        return message.trim();
    }

    public record ChatRequest(String message) {
    }
}
