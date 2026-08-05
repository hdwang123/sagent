package com.example.sagent.controller;

import com.example.sagent.agent.approval.ApprovalService;
import com.example.sagent.agent.approval.ApprovalBypass;
import com.example.sagent.agent.approval.ToolRegistry;
import com.example.sagent.agent.model.ApprovalRecord;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 审批控制器
 * 提供审批记录查询、审批通过（重新执行工具）、审批拒绝三个端点
 */
@RestController
@RequestMapping("/ai/approvals")
public class ApprovalController {

    private final ApprovalService approvalService;
    private final ToolRegistry toolRegistry;

    public ApprovalController(ApprovalService approvalService, ToolRegistry toolRegistry) {
        this.approvalService = approvalService;
        this.toolRegistry = toolRegistry;
    }

    /**
     * 查询全部审批记录（按创建时间倒序）
     *
     * @return 审批记录列表
     */
    @GetMapping("/all")
    public List<ApprovalRecord> listAll() {
        return approvalService.listAll();
    }

    /**
     * 查询待审批记录（仅 status=PENDING）
     *
     * @return 待审批记录列表
     */
    @GetMapping("/pending")
    public List<ApprovalRecord> listPending() {
        return approvalService.listPending();
    }

    /**
     * 审批通过：通过 ApprovalBypass 绕过切面，重新调用原始工具并记录执行结果
     *
     * @param id 审批记录ID
     * @return 包含 success 与 result/error 的响应体
     */
    @PostMapping("/{id}/approve")
    public ResponseEntity<Map<String, Object>> approve(@PathVariable String id) {
        try {
            ApprovalRecord record = approvalService.getRecord(id);
            if (!"PENDING".equals(record.status())) {
                return ResponseEntity.badRequest().body(Map.of(
                        "success", false,
                        "error", "该记录已被处理，状态为: " + record.status()
                ));
            }
            String result;
            ApprovalBypass.enable();
            try {
                result = toolRegistry.resolveTool(record.toolName()).call(record.argsJson());
            } finally {
                ApprovalBypass.disable();
            }
            approvalService.approve(id, result);

            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }

    /**
     * 审批拒绝：将记录状态更新为 REJECTED
     *
     * @param id 审批记录ID
     * @return 包含 success 与 result/error 的响应体
     */
    @PostMapping("/{id}/reject")
    public ResponseEntity<Map<String, Object>> reject(@PathVariable String id) {
        try {
            approvalService.reject(id);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("result", "已拒绝");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> error = new HashMap<>();
            error.put("success", false);
            error.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(error);
        }
    }
}
