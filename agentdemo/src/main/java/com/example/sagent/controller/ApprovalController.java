package com.example.sagent.controller;

import com.example.sagent.agent.approval.ApprovalService;
import com.example.sagent.agent.approval.ApprovalBypass;
import com.example.sagent.agent.approval.ToolRegistry;
import com.example.sagent.agent.model.ApprovalRecord;
import com.example.sagent.agent.model.Product;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/ai/approvals")
public class ApprovalController {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ApprovalService approvalService;
    private final ToolRegistry toolRegistry;
    private final JdbcClient jdbcClient;

    public ApprovalController(ApprovalService approvalService, ToolRegistry toolRegistry, JdbcClient jdbcClient) {
        this.approvalService = approvalService;
        this.toolRegistry = toolRegistry;
        this.jdbcClient = jdbcClient;
    }

    @GetMapping("/all")
    public List<ApprovalRecord> listAll() {
        return approvalService.listAll();
    }

    @GetMapping("/pending")
    public List<ApprovalRecord> listPending() {
        return approvalService.listPending();
    }

    @GetMapping("/products")
    public List<Product> listProducts() {
        return jdbcClient.sql("select id, name, category, price, stock from products order by id")
                .query((rs, rowNum) -> new Product(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getString("category"),
                        rs.getBigDecimal("price"),
                        rs.getInt("stock")
                )).list();
    }

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
            ToolRegistry.ToolEntry entry = toolRegistry.getTool(record.toolName());
            Object[] args = resolveArgs(entry.method(), record.argsJson());
            String result;
            ApprovalBypass.enable();
            try {
                result = toolRegistry.invokeTool(record.toolName(), args);
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

    private Object[] resolveArgs(java.lang.reflect.Method method, String argsJson) throws Exception {
        Map<String, Object> argMap = MAPPER.readValue(argsJson,
                new TypeReference<Map<String, Object>>() {});
        java.lang.reflect.Parameter[] params = method.getParameters();
        Object[] result = new Object[params.length];
        for (int i = 0; i < params.length; i++) {
            Object raw = argMap.get(params[i].getName());
            if (raw != null) {
                result[i] = MAPPER.convertValue(raw, params[i].getType());
            }
        }
        return result;
    }
}
