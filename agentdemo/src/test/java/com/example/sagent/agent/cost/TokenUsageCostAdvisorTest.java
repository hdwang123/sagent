package com.example.sagent.agent.cost;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TokenUsageCostAdvisor 单元测试
 * <p>
 * 验证工具循环内层 advisor 的统计链路：
 * 从 advisor param 读取 conversationId/operationType，并从 ChatResponse 提取 usage 调用 CostMonitorService。
 */
class TokenUsageCostAdvisorTest {

    @Test
    void adviseCall_readsParamsAndRecordsCost() {
        CostMonitorService costMonitorService = mock(CostMonitorService.class);
        TokenUsageCostAdvisor advisor = new TokenUsageCostAdvisor(costMonitorService);

        ChatResponse chatResponse = mock(ChatResponse.class);
        ChatResponseMetadata metadata = mock(ChatResponseMetadata.class);
        Usage usage = mock(Usage.class);
        when(metadata.getModel()).thenReturn("deepseek-chat");
        when(metadata.getUsage()).thenReturn(usage);
        when(usage.getPromptTokens()).thenReturn(10);
        when(usage.getCompletionTokens()).thenReturn(5);
        when(chatResponse.getMetadata()).thenReturn(metadata);

        ChatClientResponse response = mock(ChatClientResponse.class);
        when(response.chatResponse()).thenReturn(chatResponse);

        ChatClientRequest request = mock(ChatClientRequest.class);
        when(request.context()).thenReturn(Map.of(
                ChatMemory.CONVERSATION_ID, "conv-1",
                "operationType", "agent/skill"));

        CallAdvisorChain chain = mock(CallAdvisorChain.class);
        when(chain.nextCall(request)).thenReturn(response);

        ChatClientResponse result = advisor.adviseCall(request, chain);

        // 透传 response，并携带参数记账
        verify(chain).nextCall(request);
        verify(costMonitorService).saveCostRecord(eq("conv-1"), eq("agent/skill"), eq(chatResponse));
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
                org.mockito.ArgumentMatchers.any(ChatResponse.class));
    }
}
