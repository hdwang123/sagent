package com.example.sagent.agent.approval;

import com.example.sagent.agent.model.ApprovalRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 审批服务
 * 负责审批记录的业务流程：创建、查询、审批通过/拒绝
 * 数据访问委托给 ApprovalRepository
 */
@Service
public class ApprovalService {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApprovalService.class);

    private final ApprovalRepository approvalRepository;

    public ApprovalService(ApprovalRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    public ApprovalRecord createPending(String userId, String toolName, String argsJson) {
        ApprovalRecord record = approvalRepository.createPending(userId, toolName, argsJson);
        LOGGER.info("create PENDING #{}: {} {} (user={})", record.id(), toolName, argsJson, userId);
        return record;
    }

    public Optional<ApprovalRecord> findExisting(String userId, String toolName, String argsJson) {
        return approvalRepository.findExisting(userId, toolName, argsJson);
    }

    public List<ApprovalRecord> listAll() {
        var records = approvalRepository.listAll();
        LOGGER.info("listAll: found {} records", records.size());
        return records;
    }

    public List<ApprovalRecord> listPending() {
        var records = approvalRepository.listPending();
        LOGGER.info("listPending: found {} PENDING records", records.size());
        return records;
    }

    public ApprovalRecord getRecord(String id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("approval not found: " + id));
    }

    public void approve(String id, String executionResult) {
        ApprovalRecord record = getRecord(id);
        if (!"PENDING".equals(record.status())) {
            throw new IllegalStateException("status is not PENDING: " + record.status());
        }
        approvalRepository.updateStatus(id, "APPROVED", executionResult);
        LOGGER.info("approve #{}: {}", id, executionResult);
    }

    public void reject(String id) {
        ApprovalRecord record = getRecord(id);
        if (!"PENDING".equals(record.status())) {
            throw new IllegalStateException("status is not PENDING: " + record.status());
        }
        approvalRepository.updateStatus(id, "REJECTED", "rejected");
        LOGGER.info("reject #{}", id);
    }

    public Optional<String> getApprovedResult(String userId, String toolName, String argsJson) {
        return approvalRepository.getApprovedResult(userId, toolName, argsJson);
    }
}
