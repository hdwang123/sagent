package com.example.sagent.agent.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ModelPricing 容错匹配单元测试
 * <p>
 * 覆盖：精确匹配、大小写不敏感前缀匹配（deepseek-chat-xxx 命中 deepseek-chat）、
 * null/未知模型兜底默认模型。保证任何模型名都能得到定价，token 记录不被丢弃。
 * 定价数据对应 application.yml 中 agent.cost.pricing 的 DeepSeek 官方平时价。
 */
class ModelPricingTest {

    private ModelPricing pricing;

    @BeforeEach
    void setUp() {
        pricing = new ModelPricing("deepseek-v4-flash", Map.of(
                "deepseek-v4-flash", new ModelPricing.Pricing(BigDecimal.valueOf(0.001), BigDecimal.valueOf(0.002)),
                "deepseek-v4-pro", new ModelPricing.Pricing(BigDecimal.valueOf(0.003), BigDecimal.valueOf(0.006)),
                "deepseek-chat", new ModelPricing.Pricing(BigDecimal.valueOf(0.001), BigDecimal.valueOf(0.002))
        ));
    }

    @Test
    void get_exactMatch_returnsPricing() {
        assertThat(pricing.get("deepseek-v4-flash")).isNotNull();
        assertThat(pricing.get("deepseek-v4-pro")).isNotNull();
        assertThat(pricing.get("deepseek-chat")).isNotNull();
    }

    @Test
    void get_prefixMatch_returnsDeepSeekPricing() {
        ModelPricing.Pricing p = pricing.get("deepseek-chat-20250701");
        assertThat(p).isNotNull();
        assertThat(p.inputPricePer1k()).isEqualByComparingTo("0.001");
        assertThat(p.outputPricePer1k()).isEqualByComparingTo("0.002");
    }

    @Test
    void get_caseInsensitivePrefixMatch_returnsPricing() {
        ModelPricing.Pricing p = pricing.get("DeepSeek-Chat");
        assertThat(p).isNotNull();
        assertThat(p.inputPricePer1k()).isEqualByComparingTo("0.001");
    }

    @Test
    void get_nullOrUnknown_fallsBackToDefault() {
        assertThat(pricing.get(null)).isNotNull();
        assertThat(pricing.get("")).isNotNull();
        assertThat(pricing.get("some-unknown-model")).isNotNull();
        // 兜底均为默认模型（deepseek-v4-flash）定价
        assertThat(pricing.get("unknown-model").inputPricePer1k())
                .isEqualByComparingTo(pricing.get("deepseek-v4-flash").inputPricePer1k());
    }

    @Test
    void get_emptyPricingMap_returnsZeroPricing() {
        ModelPricing empty = new ModelPricing("deepseek-v4-flash", Map.of());
        ModelPricing.Pricing p = empty.get("anything");
        assertThat(p.inputPricePer1k()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(p.outputPricePer1k()).isEqualByComparingTo(BigDecimal.ZERO);
    }
}
