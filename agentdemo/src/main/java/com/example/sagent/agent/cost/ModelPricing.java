package com.example.sagent.agent.cost;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 模型定价配置
 * <p>
 * 从 {@code application.yml} 的 {@code agent.cost.pricing} 读取各模型每 1000 tokens 单价
 * （人民币计价），支持外部化配置与运行时调整，无需改代码即可适配价格变动。
 * 定价数据来源：DeepSeek 官方定价（api-docs.deepseek.com，2026-08-17 起启用峰谷分时，
 * 本配置采用高峰档单价统一计费，空闲时段会高估约 100%，不引入运行时时段判断）。
 */
@Component
@ConfigurationProperties(prefix = "agent.cost.pricing")
public class ModelPricing {

    /** 默认兜底模型名 */
    private String defaultModel = "deepseek-v4-flash";

    /** 模型定价表：模型名 → 每 1000 tokens 单价（人民币） */
    private Map<String, Pricing> models = new HashMap<>();

    /** JPA/Spring 绑定用无参构造 */
    public ModelPricing() {
    }

    /**
     * 显式构造（供单元测试直接装配，不依赖 Spring 上下文）。
     *
     * @param defaultModel 默认兜底模型名
     * @param models       模型定价表
     */
    public ModelPricing(String defaultModel, Map<String, Pricing> models) {
        this.defaultModel = defaultModel;
        this.models = models;
    }

    public String getDefaultModel() {
        return defaultModel;
    }

    public void setDefaultModel(String defaultModel) {
        this.defaultModel = defaultModel;
    }

    public Map<String, Pricing> getModels() {
        return models;
    }

    public void setModels(Map<String, Pricing> models) {
        this.models = models;
    }

    /**
     * 获取指定模型的定价信息（容错匹配）
     * <p>
     * 匹配顺序：精确匹配 → 大小写不敏感前缀匹配（如 deepseek-v4-flash-xxx 命中 deepseek-v4-flash）→
     * 默认兜底模型定价。保证任何模型名都能得到定价，避免 token 记录被丢弃。
     *
     * @param modelName 模型名称（可能为 null）
     * @return 匹配到的定价；表为空时返回默认兜底定价
     */
    public Pricing get(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            return fallback();
        }
        Pricing exact = models.get(modelName);
        if (exact != null) {
            return exact;
        }
        String lower = modelName.toLowerCase();
        for (Map.Entry<String, Pricing> entry : models.entrySet()) {
            if (lower.startsWith(entry.getKey().toLowerCase())) {
                return entry.getValue();
            }
        }
        return fallback();
    }

    /**
     * 默认兜底定价：优先取配置的默认模型，否则返回空定价（各项均为 0）。
     *
     * @return 兜底定价
     */
    private Pricing fallback() {
        Pricing p = models.get(defaultModel);
        return p != null ? p : new Pricing(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
    }

    /**
     * 模型定价记录
     *
     * @param inputPricePer1k           输入 token（缓存未命中）单价（人民币/每 1000 tokens）
     * @param outputPricePer1k          输出 token 单价（人民币/每 1000 tokens）
     * @param cacheReadInputPricePer1k  输入 token（缓存命中）单价（人民币/每 1000 tokens）
     */
    public record Pricing(BigDecimal inputPricePer1k, BigDecimal outputPricePer1k,
                          BigDecimal cacheReadInputPricePer1k) {
    }
}
