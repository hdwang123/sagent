package com.example.sagent.agent.model;

import java.math.BigDecimal;

/**
 * 产品数据模型
 * 封装产品信息
 */
public record Product(
        /**
         * 产品ID
         */
        Long id,
        /**
         * 产品名称
         */
        String name,
        /**
         * 产品分类
         */
        String category,
        /**
         * 产品价格
         */
        BigDecimal price,
        /**
         * 产品库存
         */
        Integer stock
) {
}