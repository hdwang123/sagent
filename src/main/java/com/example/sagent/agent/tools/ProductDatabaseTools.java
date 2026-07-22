package com.example.sagent.agent.tools;

import com.example.sagent.agent.model.Product;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * 产品数据库工具
 * 提供产品数据的查询功能
 */
@Component
public class ProductDatabaseTools {

    /**
     * 产品表字段
     */
    private static final String PRODUCT_COLUMNS = "id, name, category, price, stock";

    private final JdbcClient jdbcClient;

    /**
     * 构造函数
     *
     * @param jdbcClient JDBC客户端
     */
    public ProductDatabaseTools(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    /**
     * 查询全部产品
     * 最多返回20条记录
     *
     * @return 产品列表
     */
    @Tool(description = "查询全部产品，最多返回20条")
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

    /**
     * 按名称查询产品
     * 根据关键词模糊查询产品
     *
     * @param keyword 产品名称关键词
     * @return 产品列表
     */
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

    /**
     * 按价格查询产品
     * 查询价格不高于指定金额的产品
     *
     * @param maxPrice 最高价格
     * @return 产品列表
     */
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

    /**
     * 按ID查询产品
     * 查询单个产品信息
     *
     * @param id 产品ID
     * @return 产品信息（可选）
     */
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

    /**
     * 统计产品数量
     *
     * @return 产品总数
     */
    @Tool(description = "统计产品总数量")
    public int countProducts() {
        return jdbcClient.sql("select count(*) from products")
                .query(Integer.class)
                .single();
    }
}