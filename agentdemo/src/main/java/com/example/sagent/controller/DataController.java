package com.example.sagent.controller;

import com.example.sagent.agent.model.ApprovalRecord;
import com.example.sagent.agent.model.Product;
import com.example.sagent.agent.approval.ApprovalService;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class DataController {

    private final JdbcClient jdbcClient;
    private final ApprovalService approvalService;

    public DataController(JdbcClient jdbcClient, ApprovalService approvalService) {
        this.jdbcClient = jdbcClient;
        this.approvalService = approvalService;
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

    @GetMapping("/approvals")
    public List<ApprovalRecord> listApprovals() {
        return approvalService.listAll();
    }
}
