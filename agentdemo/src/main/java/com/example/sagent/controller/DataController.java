package com.example.sagent.controller;

import com.example.sagent.agent.model.ApprovalRecord;
import com.example.sagent.agent.model.Product;
import com.example.sagent.agent.approval.ApprovalService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 数据控制器
 * 提供产品列表与审批记录列表两个只读查询端点，供前端数据展示
 */
@RestController
@RequestMapping("/api")
public class DataController {

    private final JdbcClient jdbcClient;
    private final ApprovalService approvalService;

    public DataController(JdbcClient jdbcClient, ApprovalService approvalService) {
        this.jdbcClient = jdbcClient;
        this.approvalService = approvalService;
    }

    /**
     * 查询全部产品（按ID升序）
     *
     * @return 产品列表
     */
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

    /**
     * 查询全部审批记录（与 /ai/approvals/all 等价，提供 /api 前缀的便捷访问）
     *
     * @return 审批记录列表
     */
    @GetMapping("/approvals")
    public List<ApprovalRecord> listApprovals() {
        return approvalService.listAll();
    }
}
