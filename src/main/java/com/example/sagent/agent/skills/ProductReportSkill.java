package com.example.sagent.agent.skills;

import com.example.sagent.agent.model.Product;
import com.example.sagent.agent.tools.CompressionTool;
import com.example.sagent.agent.tools.DocumentTool;
import com.example.sagent.agent.tools.ProductDatabaseTools;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 产品报告技能
 * 提供查询产品数据、生成报告文档、压缩下载功能
 */
@Component
public class ProductReportSkill implements Skill {

    /**
     * 技能描述
     */
    private static final String DESCRIPTION = "产品报告生成：查询产品数据、生成报告文档、压缩下载";

    private final ProductDatabaseTools productDatabaseTools;
    private final DocumentTool documentTool;
    private final CompressionTool compressionTool;

    /**
     * 构造函数
     *
     * @param productDatabaseTools 产品数据库工具
     * @param documentTool         文档工具
     * @param compressionTool      压缩工具
     */
    public ProductReportSkill(
            ProductDatabaseTools productDatabaseTools,
            DocumentTool documentTool,
            CompressionTool compressionTool
    ) {
        this.productDatabaseTools = productDatabaseTools;
        this.documentTool = documentTool;
        this.compressionTool = compressionTool;
    }

    /**
     * 获取技能名称
     *
     * @return 技能名称
     */
    @Override
    public String getName() {
        return "ProductReport";
    }

    /**
     * 获取技能描述
     *
     * @return 技能描述
     */
    @Override
    public String getDescription() {
        return DESCRIPTION;
    }

    /**
     * 生成产品报告
     * 查询数据库产品数据，生成Markdown报告文档，可选压缩打包
     *
     * @param title    报告标题
     * @param compress 是否压缩打包
     * @return 报告下载链接
     */
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