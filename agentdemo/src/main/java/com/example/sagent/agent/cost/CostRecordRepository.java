package com.example.sagent.agent.cost;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 成本记录 Repository
 */
public interface CostRecordRepository extends JpaRepository<CostRecord, Long>, JpaSpecificationExecutor<CostRecord> {

    /**
     * 查询指定用户和日期范围内的成本记录
     */
    List<CostRecord> findByUserIdAndCreatedAtBetweenOrderByCreatedAtDesc(String userId, LocalDateTime start, LocalDateTime end);

    /**
     * 查询指定模型和日期范围内的成本记录
     */
    List<CostRecord> findByModelNameAndCreatedAtBetweenOrderByCreatedAtDesc(String modelName, LocalDateTime start, LocalDateTime end);

    /**
     * 查询指定用户的全部成本记录（按时间倒序）
     */
    List<CostRecord> findByUserIdOrderByCreatedAtDesc(String userId);
}
