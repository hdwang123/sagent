package com.example.sagent.agent.agents.database;

import java.math.BigDecimal;

public record Product(
        Long id,
        String name,
        String category,
        BigDecimal price,
        Integer stock
) {
}
