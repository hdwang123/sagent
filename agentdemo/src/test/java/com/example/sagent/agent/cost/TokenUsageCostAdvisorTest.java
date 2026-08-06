package com.example.sagent.agent.cost;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TokenUsageCostAdvisor 单元测试
 * <p>
 * 验证工具循环内层 advisor 的统计链路：
 * 从 advisor param 读取 conversationId/operationType，并将请求+响应一并传给 CostMonitorService
 * （用于记录 LLM 输入输出内容）。
 */
class TokenUsageCostAdvisorTest {

    @Test
    void adviseCall_readsParamsAndRecordsCost() {
        CostMonitorService costMonitorService = mock(CostMonitorService.class);
        TokenUsageCostAdvisor advisor = new TokenUsageCostAdvisor(costMonitorService);

        ChatClientResponse response = mock(ChatClientResponse.class);

        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.context()).thenReturn(Map.of(
                ChatMemory.CONVERSATION_ID, "conv-1",
                "operationType", "agent/skill"));

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        // 透传 response，并携带请求+响应记账（含输入输出内容）
        verify(chain).nextCall(request);
        verify(costMonitorService).saveCostRecord(eq("conv-1"), eq("agent/skill"), eq(request), eq(response));
    }

    @Test
    void adviseCall_missingOperationType_skipsRecording() {
        CostMonitorService costMonitorService = mock(CostMonitorService.class);
        TokenUsageCostAdvisor advisor = new TokenUsageCostAdvisor(costMonitorService);

        ChatClientResponse response = mock(ChatClientResponse.class);
        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.context()).thenReturn(Map.of(ChatMemory.CONVERSATION_ID, "conv-1"));

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        advisor.adviseCall(request, chain);

        verify(chain).nextCall(request);
        verify(costMonitorService, org.mockito.Mockito.never()).saveCostRecord(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any(ChatClientRequest.class),
                org.mockito.ArgumentMatchers.any(ChatClientResponse.class));
    }
}
