package com.example.sagent.controller;

import com.example.sagent.agent.core.AgentService;
import com.example.sagent.agent.base.model.AgentResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

@RestController
@RequestMapping("/ai")
public class ChatController {

    private final AgentService agentService;
    private final ChatMemory chatMemory;

    public ChatController(AgentService agentService, ChatMemory chatMemory) {
        this.agentService = agentService;
        this.chatMemory = chatMemory;
    }

    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody ChatRequest request) {
        String conversationId = requireConversationId(request.conversationId());
        return agentService.ask(conversationId, requireMessage(request.message()));
    }

    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearConversation(@PathVariable String conversationId) {
        chatMemory.clear(requireConversationId(conversationId));
    }

    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "message must not be blank");
        }
        return message.trim();
    }

    private String requireConversationId(String conversationId) {
        if (conversationId == null || conversationId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        String normalized = conversationId.trim();
        if (normalized.length() > 128) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "conversationId must not exceed 128 characters"
            );
        }
        return normalized;
    }

    public record ChatRequest(String conversationId, String message) {
    }
}
