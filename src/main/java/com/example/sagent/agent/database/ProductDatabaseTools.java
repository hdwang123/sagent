package com.example.sagent.agent.database;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Component
public class ProductDatabaseTools {

    private static final String PRODUCT_COLUMNS = "id, name, category, price, stock";

    private final JdbcClient jdbcClient;

    public ProductDatabaseTools(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Tool(description = "查询全部产品，最多返回20条。适用于查看产品列表、库存和价格")
    public List<Product> listProducts() {
        return jdbcClient.sql("""
                        select %s
                        from products
                        order by id
                        limit 20
                        """.formatted(PRODUCT_COLUMNS))
                .query(Product.class)
                .list();
    }

    @Tool(description = "按产品名称模糊查询产品，最多返回20条")
    public List<Product> findProductsByName(
            @ToolParam(description = "产品名称关键词") String keyword
    ) {
        return jdbcClient.sql("""
                        select %s
                        from products
                        where lower(name) like lower(:keyword)
                        order by id
                        limit 20
                        """.formatted(PRODUCT_COLUMNS))
                .param("keyword", "%" + keyword + "%")
                .query(Product.class)
                .list();
    }

    @Tool(description = "查询价格不高于指定金额的产品，最多返回20条")
    public List<Product> findProductsByMaxPrice(
            @ToolParam(description = "最高价格，必须是大于等于0的数字") BigDecimal maxPrice
    ) {
        if (maxPrice.signum() < 0) {
            throw new IllegalArgumentException("Maximum price must not be negative");
        }
        return jdbcClient.sql("""
                        select %s
                        from products
                        where price <= :maxPrice
                        order by price, id
                        limit 20
                        """.formatted(PRODUCT_COLUMNS))
                .param("maxPrice", maxPrice)
                .query(Product.class)
                .list();
    }

    @Tool(description = "按产品ID查询单个产品")
    public Optional<Product> findProductById(
            @ToolParam(description = "产品ID，必须是正整数") Long id
    ) {
        return jdbcClient.sql("""
                        select %s
                        from products
                        where id = :id
                        """.formatted(PRODUCT_COLUMNS))
                .param("id", id)
                .query(Product.class)
                .optional();
    }

    @Tool(description = "统计产品总数量")
    public int countProducts() {
        return jdbcClient.sql("select count(*) from products")
                .query(Integer.class)
                .single();
    }
}
