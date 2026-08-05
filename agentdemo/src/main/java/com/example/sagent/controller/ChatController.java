package com.example.sagent.controller;

import com.example.sagent.agent.core.AgentService;
import com.example.sagent.agent.multi.MultiAgentService;
import com.example.sagent.agent.model.AgentResponse;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Qualifier;
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

/**
 * 聊天控制器
 * 提供单Agent对话、多Agent编排、会话清理三个 RESTful 端点
 */
@RestController
@RequestMapping("/ai")
public class ChatController {

    private final AgentService agentService;
    private final MultiAgentService multiAgentService;
    private final ChatMemory chatMemory;

    public ChatController(
            AgentService agentService,
            MultiAgentService multiAgentService,
            @Qualifier("chatMemory") ChatMemory chatMemory
    ) {
        this.agentService = agentService;
        this.multiAgentService = multiAgentService;
        this.chatMemory = chatMemory;
    }

    /**
     * 单Agent对话端点
     * 经 MessageClassifier 路由到对应 Handler 处理并返回响应
     *
     * @param request 包含 conversationId 与 message 的请求体
     * @return Agent 响应
     */
    @PostMapping("/chat")
    public AgentResponse chat(@RequestBody ChatRequest request) {
        String conversationId = requireConversationId(request.conversationId());
        return agentService.ask(conversationId, requireMessage(request.message()));
    }

    /**
     * 多Agent编排演示端点
     * Planner拆解任务 -> 复用现有Handler并行执行 -> 汇总生成最终回答
     *
     * @param request 包含 conversationId 与 message 的请求体
     * @return Agent 响应，type 为 "multi-agent"
     */
    @PostMapping("/multi-agent")
    public AgentResponse multiAgent(@RequestBody ChatRequest request) {
        String conversationId = requireConversationId(request.conversationId());
        String message = requireMessage(request.message());
        var result = multiAgentService.handle(conversationId, message);
        return new AgentResponse(
                conversationId,
                result.answer(),
                null,
                "multi-agent",
                result.sources(),
                result.code()
        );
    }

    /**
     * 清理指定会话的聊天记忆
     *
     * @param conversationId 会话ID
     */
    @DeleteMapping("/conversations/{conversationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void clearConversation(@PathVariable String conversationId) {
        chatMemory.clear(requireConversationId(conversationId));
    }

    /**
     * 校验消息非空，否则抛出 400 异常
     */
    private String requireMessage(String message) {
        if (message == null || message.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "message must not be blank");
        }
        return message.trim();
    }

    /**
     * 校验并规范化 conversationId：为空时生成随机UUID，超长（>128）时抛出 400 异常
     */
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

    /**
     * 聊天请求体
     *
     * @param conversationId 会话ID，为空时服务端自动生成
     * @param message 用户消息
     */
    public record ChatRequest(String conversationId, String message) {
    }
}