package com.example.sagent.agent.cost;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelPricing 容错匹配单元测试
 * <p>
 * 覆盖：精确匹配、大小写不敏感前缀匹配（deepseek-chat-xxx 命中 deepseek-chat）、
 * null/未知模型兜底 deepseek-chat。保证任何模型名都能得到定价，token 记录不被丢弃。
 */
class ModelPricingTest {

    @Test
    void get_exactMatch_returnsPricing() {
        assertThat(ModelPricing.get("deepseek-chat")).isNotNull();
        assertThat(ModelPricing.get("openai-gpt-4")).isNotNull();
    }

    @Test
    void get_prefixMatch_returnsDeepSeekPricing() {
        ModelPricing.Pricing p = ModelPricing.get("deepseek-chat-20250701");
        assertThat(p).isNotNull();
        assertThat(p.inputPricePer1k()).isEqualByComparingTo("0.002");
        assertThat(p.outputPricePer1k()).isEqualByComparingTo("0.008");
    }

    @Test
    void get_caseInsensitivePrefixMatch_returnsPricing() {
        ModelPricing.Pricing p = ModelPricing.get("DeepSeek-Chat");
        assertThat(p).isNotNull();
    }

    @Test
    void get_nullOrUnknown_fallsBackToDefault() {
        assertThat(ModelPricing.get(null)).isNotNull();
        assertThat(ModelPricing.get("")).isNotNull();
        assertThat(ModelPricing.get("some-unknown-model")).isNotNull();
        // 兜底均为 deepseek-chat 定价
        assertThat(ModelPricing.get("unknown-model").inputPricePer1k())
                .isEqualByComparingTo(ModelPricing.get("deepseek-chat").inputPricePer1k());
    }
}
