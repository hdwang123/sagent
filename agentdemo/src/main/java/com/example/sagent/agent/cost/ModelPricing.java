package com.example.sagent.agent.cost;

import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 模型定价配置
 * 根据模型名称获取输入和输出 token 的单价（每 1000 tokens，人民币计价）
 */
public class ModelPricing {

    private static final Map<String, Pricing> PRICING = new ConcurrentHashMap<>();

    static {
        // DeepSeek Chat（示例定价：¥0.002/1K input, ¥0.008/1K output）
        PRICING.put("deepseek-chat", new Pricing(
                BigDecimal.valueOf(0.002),
                BigDecimal.valueOf(0.008)
        ));
        // OpenAI GPT-4（示例定价，按 $0.03/$0.06 × 汇率 7.2 折算）
        PRICING.put("openai-gpt-4", new Pricing(
                BigDecimal.valueOf(0.216),
                BigDecimal.valueOf(0.432)
        ));
        // OpenAI GPT-3.5（示例定价，按 $0.0005/$0.0015 × 汇率 7.2 折算）
        PRICING.put("openai-gpt-3.5-turbo", new Pricing(
                BigDecimal.valueOf(0.0036),
                BigDecimal.valueOf(0.0108)
        ));
    }

    /**
     * 获取指定模型的定价信息（容错匹配）
     * <p>
     * 匹配顺序：精确匹配 → 大小写不敏感前缀匹配（如 deepseek-chat-xxx 命中 deepseek-chat）→
     * 默认兜底 deepseek-chat 定价。保证任何模型名都能得到定价，避免 token 记录被丢弃。
     */
    public static Pricing get(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return PRICING.get("deepseek-chat");
        }
        Pricing exact = PRICING.get(modelName);
        if (exact != null) {
            return exact;
        }
        String lower = modelName.toLowerCase();
        for (Map.Entry<String, Pricing> entry : PRICING.entrySet()) {
            if (lower.startsWith(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return PRICING.get("deepseek-chat");
    }

    /**
     * 模型定价记录
     */
    public record Pricing(BigDecimal inputPricePer1k, BigDecimal outputPricePer1k) {
    }
}
