package com.example.sagent.agent.agents.skill.skills;

import com.example.sagent.agent.agents.database.ProductDatabaseTools;
import com.example.sagent.agent.agents.skill.tool.CompressionTool;
import com.example.sagent.agent.agents.skill.tool.DocumentTool;
import com.example.sagent.agent.agents.database.Product;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class ProductReportSkill implements Skill {

    private static final String DESCRIPTION = "产品报告生成：查询产品数据、生成报告文档、压缩下载";

    private final ProductDatabaseTools productDatabaseTools;
    private final DocumentTool documentTool;
    private final CompressionTool compressionTool;

    public ProductReportSkill(
            ProductDatabaseTools productDatabaseTools,
            DocumentTool documentTool,
            CompressionTool compressionTool
    ) {
        this.productDatabaseTools = productDatabaseTools;
        this.documentTool = documentTool;
        this.compressionTool = compressionTool;
    }

    @Override
    public String getName() {
        return "ProductReport";
    }

    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    @Tool(description = "生成产品报告并提供下载链接，包含查询数据库、生成文档、压缩打包步骤")
    public String generateProductReport(
            @ToolParam(description = "报告标题") String title,
            @ToolParam(description = "是否压缩打包") boolean compress
    ) {
        List<Product> products = productDatabaseTools.listProducts();
        
        StringBuilder content = new StringBuilder();
        content.append("# ").append(title).append("\n\n");
        content.append("## 产品列表\n\n");
        content.append("| ID | 名称 | 分类 | 价格 | 库存 |\n");
        content.append("|----|------|------|------|------|\n");
        for (Product product : products) {
            content.append("| ").append(product.id())
                    .append(" | ").append(product.name())
                    .append(" | ").append(product.category())
                    .append(" | ").append(product.price())
                    .append(" | ").append(product.stock())
                    .append(" |\n");
        }
        content.append("\n**产品总数：").append(products.size()).append("**\n");

        documentTool.generateMarkdownDocument(title, content.toString());

        if (compress) {
            String fileName = title + ".md";
            compressionTool.compressFiles(fileName, title + "_report");
            return "产品报告已生成并压缩，下载链接: /files/download/" + title + "_report.zip";
        }

        return "产品报告已生成，下载链接: /files/download/" + title + ".md";
    }
}