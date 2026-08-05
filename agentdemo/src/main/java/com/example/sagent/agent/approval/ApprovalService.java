package com.example.sagent.agent.approval;

import com.example.sagent.agent.audit.AuditLog;
import com.example.sagent.agent.audit.OperationType;
import com.example.sagent.agent.audit.ResourceType;
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

    /**
     * 创建 PENDING 审批记录
     *
     * @param userId 用户ID
     * @param toolName 工具方法名
     * @param argsJson 参数JSON
     * @return 新建的审批记录
     */
    @AuditLog(operationType = OperationType.APPROVAL_SUBMIT,
            resourceType = ResourceType.APPROVAL,
            resourceId = "createPending",
            operationDetail = "提交审批申请")
    public ApprovalRecord createPending(String userId, String toolName, String argsJson) {
        ApprovalRecord record = approvalRepository.createPending(userId, toolName, argsJson);
        LOGGER.info("create PENDING #{}: {} {} (user={})", record.id(), toolName, argsJson, userId);
        return record;
    }

    /**
     * 查找已存在的 PENDING 记录（避免重复创建）
     *
     * @param userId 用户ID
     * @param toolName 工具方法名
     * @param argsJson 参数JSON
     * @return 命中的 PENDING 记录，无则 empty
     */
    public Optional<ApprovalRecord> findExisting(String userId, String toolName, String argsJson) {
        return approvalRepository.findExisting(userId, toolName, argsJson);
    }

    /**
     * 查询全部审批记录
     *
     * @return 审批记录列表（按创建时间倒序）
     */
    public List<ApprovalRecord> listAll() {
        var records = approvalRepository.listAll();
        LOGGER.info("listAll: found {} records", records.size());
        return records;
    }

    /**
     * 查询待审批记录（仅 PENDING）
     *
     * @return 待审批记录列表
     */
    public List<ApprovalRecord> listPending() {
        var records = approvalRepository.listPending();
        LOGGER.info("listPending: found {} PENDING records", records.size());
        return records;
    }

    /**
     * 根据ID获取审批记录，不存在时抛出 IllegalArgumentException
     *
     * @param id 审批记录ID
     * @return 审批记录
     */
    public ApprovalRecord getRecord(String id) {
        return approvalRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("approval not found: " + id));
    }

    /**
     * 审批通过：更新状态为 APPROVED 并记录执行结果
     * 仅 PENDING 状态可审批通过，否则抛出 IllegalStateException
     *
     * @param id 审批记录ID
     * @param executionResult 工具执行结果
     */
    @AuditLog(operationType = OperationType.APPROVAL_APPROVE,
            resourceType = ResourceType.APPROVAL,
            resourceId = "approve",
            operationDetail = "审批通过并执行")
    public void approve(String id, String executionResult) {
        ApprovalRecord record = getRecord(id);
        if (!"PENDING".equals(record.status())) {
            throw new IllegalStateException("status is not PENDING: " + record.status());
        }
        approvalRepository.updateStatus(id, "APPROVED", executionResult);
        LOGGER.info("approve #{}: {}", id, executionResult);
    }

    /**
     * 审批拒绝：更新状态为 REJECTED
     * 仅 PENDING 状态可拒绝，否则抛出 IllegalStateException
     *
     * @param id 审批记录ID
     */
    public void reject(String id) {
        ApprovalRecord record = getRecord(id);
        if (!"PENDING".equals(record.status())) {
            throw new IllegalStateException("status is not PENDING: " + record.status());
        }
        approvalRepository.updateStatus(id, "REJECTED", "rejected");
        LOGGER.info("reject #{}", id);
    }

    /**
     * 查找已审批通过（APPROVED）的工具执行结果，供 Handler 读取缓存
     *
     * @param userId 用户ID
     * @param toolName 工具方法名
     * @param argsJson 参数JSON
     * @return 上次审批通过的执行结果，无则 empty
     */
    public Optional<String> getApprovedResult(String userId, String toolName, String argsJson) {
        return approvalRepository.getApprovedResult(userId, toolName, argsJson);
    }
}
