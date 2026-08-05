package com.example.sagent.agent.cost;

import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.ToolCallingAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.core.Ordered;

/**
 * Token 成本统计 Advisor
 * <p>
 * Spring AI 2.0.0 的 {@link ToolCallingAdvisor} 在工具调用循环中不聚合每轮 usage，
 * {@code chatResponse().getMetadata().getUsage()} 只反映最后一轮（GitHub issue #6411）。
 * <p>
 * 本 Advisor 的 order（+400）高于 ToolCallingAdvisor（+300），在链中位于其内层，
 * 因此工具循环的每一轮 LLM 往返（首轮 + 每轮工具结果回传）都会经过 {@link #adviseCall}，
 * 逐轮调用 {@link CostMonitorService} 记录 token，保证多轮循环的 token 全部入账。
 * <p>
 * conversationId 与 operationType 由 Handler 通过 advisor param 传入：
 * {@code advisor.param(ChatMemory.CONVERSATION_ID, convId).param("operationType", "SKILL")}
 */
public class TokenUsageCostAdvisor implements CallAdvisor {

    /** 必须大于 ToolCallingAdvisor.DEFAULT_ORDER（HIGHEST_PRECEDENCE + 300），确保进入工具循环的每一轮 */
    public static final int ORDER = Ordered.HIGHEST_PRECEDENCE + 400;

    private final CostMonitorService costMonitorService;

    public TokenUsageCostAdvisor(CostMonitorService costMonitorService) {
        this.costMonitorService = costMonitorService;
    }

    @Override
    public String getName() {
        return "Token Usage Cost Advisor";
    }

    @Override
    public int getOrder() {
        return ORDER;
    }

    @Override
    public ChatClientResponse adviseCall(ChatClientRequest request, CallAdvisorChain chain) {
        ChatClientResponse response = chain.nextCall(request);
        String conversationId = (String) request.context().get(ChatMemory.CONVERSATION_ID);
        String operationType = (String) request.context().get("operationType");
        if (conversationId != null && operationType != null) {
            costMonitorService.saveCostRecord(conversationId, operationType, response.chatResponse());
        }
        return response;
    }
}
