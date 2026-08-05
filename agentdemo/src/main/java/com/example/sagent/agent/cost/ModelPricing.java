package com.example.sagent.agent.cost;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型定价配置
 * 根据模型名称获取输入和输出 token 的单价（每 1000 tokens）
 */
public class ModelPricing {

    private static final Map<String, Pricing> PRICING = new ConcurrentHashMap<>();

    static {
        // DeepSeek Chat（示例定价：$0.001/1K input, $0.002/1K output）
        PRICING.put("deepseek-chat", new Pricing(
                BigDecimal.valueOf(0.001),
                BigDecimal.valueOf(0.002)
        ));
        // OpenAI GPT-4（示例定价）
        PRICING.put("openai-gpt-4", new Pricing(
                BigDecimal.valueOf(0.03),
                BigDecimal.valueOf(0.06)
        ));
        // OpenAI GPT-3.5（示例定价）
        PRICING.put("openai-gpt-3.5-turbo", new Pricing(
                BigDecimal.valueOf(0.0005),
                BigDecimal.valueOf(0.0015)
        ));
    }

    /**
     * 获取指定模型的定价信息
     */
    public static Pricing get(String modelName) {
        return PRICING.get(modelName);
    }

    /**
     * 模型定价记录
     */
    public record Pricing(BigDecimal inputPricePer1k, BigDecimal outputPricePer1k) {
    }
}
