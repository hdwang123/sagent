package com.example.sagent.agent.cost;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * CostMonitorService 缓存命中计费单元测试
 * <p>
 * 覆盖：输入 token 命中/未命中两档计费拆分、cacheRead 为 null 时全按未命中价、
 * cacheRead 大于输入总数时按输入总数截断（防御性校验）、费用正确落库。
 */
class CostMonitorServiceTest {

    private static final BigDecimal INPUT = BigDecimal.valueOf(0.001);        // 未命中价 /1K
    private static final BigDecimal OUTPUT = BigDecimal.valueOf(0.002);        // 输出价 /1K
    private static final BigDecimal CACHE_READ = BigDecimal.valueOf(0.00002);  // 命中价 /1K

    private CostRecordRepository repository;
    private CostMonitorService service;

    @BeforeEach
    void setUp() {
        repository = mock(CostRecordRepository.class);
        ModelPricing pricing = new ModelPricing("deepseek-v4-flash", Map.of(
                "deepseek-v4-flash", new ModelPricing.Pricing(INPUT, OUTPUT, CACHE_READ)));
        service = new CostMonitorService(repository, pricing);
    }

    @Test
    void saveCostRecord_allCacheRead_chargesCacheReadPrice() {
        service.saveCostRecord("user-1", "deepseek-v4-flash", 1000L, 1000L, 0L, "agent/chat", "conv-1");

        CostRecord record = capturedRecord();
        // 1000 命中 × 0.00002/1K = 0.00002，未命中为 0
        assertThat(record.getCostCny()).isEqualByComparingTo("0.00002");
    }

    @Test
    void saveCostRecord_partialCacheRead_splitsPricing() {
        // 400 命中 + 600 未命中 + 500 输出
        service.saveCostRecord("user-1", "deepseek-v4-flash", 400L, 1000L, 500L, "agent/chat", "conv-1");

        CostRecord record = capturedRecord();
        // 400×0.00002/1K + 600×0.001/1K + 500×0.002/1K = 0.000008 + 0.0006 + 0.001
        assertThat(record.getCostCny()).isEqualByComparingTo("0.001608");
        assertThat(record.getInputTokens()).isEqualTo(1000L);
        assertThat(record.getOutputTokens()).isEqualTo(500L);
        assertThat(record.getTotalTokens()).isEqualTo(1500L);
        assertThat(record.getCacheReadInputTokens()).isEqualTo(400L);
    }

    @Test
    void saveCostRecord_nullCacheRead_chargesFullMissPrice() {
        service.saveCostRecord("user-1", "deepseek-v4-flash", null, 1000L, 0L, "agent/chat", "conv-1");

        CostRecord record = capturedRecord();
        // 无命中信息时全按未命中价：1000×0.001/1K
        assertThat(record.getCostCny()).isEqualByComparingTo("0.001");
    }

    @Test
    void saveCostRecord_cacheReadExceedsInput_clampsToInput() {
        // 防御性校验：命中数超过输入总数时按输入总数截断
        service.saveCostRecord("user-1", "deepseek-v4-flash", 1000L, 500L, 0L, "agent/chat", "conv-1");

        CostRecord record = capturedRecord();
        // 500 命中 × 0.00002/1K = 0.00001
        assertThat(record.getCostCny()).isEqualByComparingTo("0.00001");
    }

    private CostRecord capturedRecord() {
        ArgumentCaptor<CostRecord> captor = ArgumentCaptor.forClass(CostRecord.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
