package com.example.sagent.agent.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * HandlerResult 单元测试
 * <p>
 * 覆盖状态码语义（200 成功 / 4xx 业务失败 / 5xx 技术错误）、
 * 便捷构造函数默认值、success()/error() 判断逻辑。
 */
class HandlerResultTest {

    // === 默认构造函数 ===

    @Test
    void constructor_answerOnly_defaultsToSuccess() {
        HandlerResult result = new HandlerResult("回答");
        assertThat(result.answer()).isEqualTo("回答");
        assertThat(result.sources()).isEmpty();
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.success()).isTrue();
        assertThat(result.error()).isFalse();
    }

    @Test
    void constructor_answerAndSources_defaultsToSuccess() {
        HandlerResult result = new HandlerResult("回答", List.of("source1"));
        assertThat(result.sources()).containsExactly("source1");
        assertThat(result.code()).isEqualTo(200);
        assertThat(result.success()).isTrue();
    }

    // === 状态码语义 ===

    @Test
    void code_200_isSuccess() {
        HandlerResult result = new HandlerResult("ok", List.of(), 200);
        assertThat(result.success()).isTrue();
        assertThat(result.error()).isFalse();
    }

    @Test
    void code_299_isSuccess() {
        // 2xx 区间上界
        HandlerResult result = new HandlerResult("ok", List.of(), 299);
        assertThat(result.success()).isTrue();
    }

    @Test
    void code_400_isBusinessError() {
        HandlerResult result = new HandlerResult("参数非法", List.of(), 400);
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isTrue();
    }

    @Test
    void code_404_isNotFound() {
        HandlerResult result = new HandlerResult("资源不存在", List.of(), 404);
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isTrue();
    }

    @Test
    void code_500_isTechnicalError() {
        HandlerResult result = new HandlerResult("技术错误", List.of(), 500);
        assertThat(result.success()).isFalse();
        assertThat(result.error()).isTrue();
    }

    // === 常量引用 ===

    @Test
    void codeSuccessConstant_matchesAgentResult() {
        assertThat(HandlerResult.CODE_SUCCESS).isEqualTo(AgentResult.CODE_SUCCESS);
        assertThat(HandlerResult.CODE_SUCCESS).isEqualTo(200);
    }

    @Test
    void codeErrorConstant_matchesAgentResult() {
        assertThat(HandlerResult.CODE_ERROR).isEqualTo(AgentResult.CODE_BUSINESS_ERROR);
        assertThat(HandlerResult.CODE_ERROR).isEqualTo(500);
    }
}
